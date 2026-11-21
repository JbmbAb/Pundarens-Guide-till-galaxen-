package se.pundaren.guide.truth.core.paket

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Atomär installation av offlinepaketet (karta.pmtiles + valhalla_tiles.tar + platser + manifest.json).
 * Flyttad från etapp 2-prototypen (`prototyp/offline-kedja`, PaketInstallerare.kt @ 47df98c). Logiken
 * är oförändrad; JSON-läsningen går via kotlinx-serialization i stället för Androids org.json så att
 * klassen är ren Kotlin.
 *
 * Sanningslagret: ingenting här vet om Illogical Engine. Ordningen är vald så att ett avbrott
 * (strömbortfall, processen dödas) i varje läge lämnar antingen det gamla eller det nya paketet
 * aktivt, aldrig ett halvt:
 *
 * 1. Allt skrivs till `rot/tmp-<version>-<tid>/` medan SHA-256 räknas på strömmen.
 * 2. Storlek och SHA-256 jämförs med manifestet. Minsta avvikelse: katalogen tas bort, inget byts.
 * 3. Filerna synkas till disk (`FileChannel.force`).
 * 4. Katalogen byter namn atomärt till `rot/paket/<version>/`.
 * 5. Pekaren `rot/aktiv` skrivs som `aktiv.tmp`, synkas och byter namn atomärt över `aktiv`.
 * 6. Föregående version sparas (återställning), äldre tas bort. Kvarlämnade `tmp-*` städas vid start.
 */
class PaketInstallerare(private val rot: File) {

    data class Fil(val namn: String, val storlek: Long, val sha256: String)

    /** Manifestet. [ratext] är filens ordagranna innehåll; [uppgifter] är hela JSON-objektet för visning. */
    data class Manifest(
        val format: String,
        val version: String,
        val filer: List<Fil>,
        val ratext: String,
        val uppgifter: JsonObject,
    )

    data class Paket(val version: String, val katalog: File, val manifest: Manifest) {
        fun fil(namn: String) = File(katalog, namn)
        fun harFil(namn: String) = manifest.filer.any { it.namn == namn }
    }

    sealed class Utfall {
        data class Installerat(val paket: Paket, val millisekunder: Long, val redanAktivt: Boolean) : Utfall()
        data class Avvisat(val skal: String) : Utfall()
    }

    /** Var paketet läses ifrån: en katalog (adb push) eller APK:ns tillgångar. */
    fun interface Kalla {
        fun oppna(namn: String): InputStream
    }

    private val paketRot = File(rot, "paket")
    private val pekare = File(rot, "aktiv")

    companion object {
        const val FORMAT = "pundarens-offlinepaket/1"
        private val TILLATET_NAMN = Regex("^[A-Za-z0-9._-]{1,128}$")
        private val SHA256 = Regex("^[0-9a-f]{64}$")

        fun lasManifest(text: String): Manifest {
            val j = Json.parseToJsonElement(text).jsonObject
            val format = j.getValue("format").jsonPrimitive.content
            require(format == FORMAT) { "okänt paketformat: $format" }
            val version = j.getValue("version").jsonPrimitive.content
            require(TILLATET_NAMN.matches(version) && version != "." && version != "..") { "ogiltig version: $version" }
            val filer = j.getValue("filer").jsonArray.map { e ->
                val f = e.jsonObject
                val namn = f.getValue("namn").jsonPrimitive.content
                require(TILLATET_NAMN.matches(namn) && namn != "manifest.json" && !namn.startsWith(".")) { "ogiltigt filnamn: $namn" }
                val sha = f.getValue("sha256").jsonPrimitive.content.lowercase()
                require(SHA256.matches(sha)) { "ogiltig sha256 för $namn" }
                Fil(namn, f.getValue("storlek").jsonPrimitive.long, sha)
            }
            require(filer.isNotEmpty()) { "manifestet saknar filer" }
            require(filer.map { it.namn }.toSet().size == filer.size) { "dubbla filnamn i manifestet" }
            return Manifest(format, version, filer, text, j)
        }
    }

    /** Det aktiva paketet, eller null om inget är installerat eller pekaren pekar fel. */
    fun aktivt(): Paket? {
        if (!pekare.isFile) return null
        val version = pekare.readText().trim()
        val katalog = File(paketRot, version)
        val m = File(katalog, "manifest.json")
        if (!m.isFile) return null
        val manifest = runCatching { lasManifest(m.readText()) }.getOrNull() ?: return null
        if (manifest.filer.any { File(katalog, it.namn).length() != it.storlek }) return null
        return Paket(version, katalog, manifest)
    }

    /** Tar bort kvarlämnade temporära kataloger från ett avbrutet försök. */
    fun stada(): Int {
        val rester = rot.listFiles { f -> f.name.startsWith("tmp-") || f.name == "aktiv.tmp" }.orEmpty()
        rester.forEach { it.deleteRecursively() }
        return rester.size
    }

    fun installera(kalla: Kalla): Utfall {
        val start = System.nanoTime()
        rot.mkdirs(); paketRot.mkdirs()
        stada()
        val manifest = try {
            lasManifest(kalla.oppna("manifest.json").use { it.readBytes().decodeToString() })
        } catch (e: Exception) {
            return Utfall.Avvisat("manifestet kunde inte läsas: ${e.message}")
        }
        aktivt()?.let { if (it.version == manifest.version) return Utfall.Installerat(it, ms(start), redanAktivt = true) }

        val tmp = File(rot, "tmp-${manifest.version}-${System.nanoTime()}")
        try {
            tmp.mkdirs()
            for (f in manifest.filer) {
                val mal = File(tmp, f.namn)
                val (storlek, sha) = kalla.oppna(f.namn).use { kopiera(it, mal) }
                if (storlek != f.storlek) return avvisa(tmp, "${f.namn}: storlek $storlek, manifestet säger ${f.storlek}")
                if (sha != f.sha256) return avvisa(tmp, "${f.namn}: SHA-256 stämmer inte ($sha)")
            }
            skrivSynkat(File(tmp, "manifest.json"), manifest.ratext.toByteArray())
            val slutlig = File(paketRot, manifest.version)
            if (slutlig.exists()) slutlig.deleteRecursively() // en hel men inaktiv kopia från ett tidigare försök
            flyttaAtomart(tmp, slutlig)
            val foregaende = if (pekare.isFile) pekare.readText().trim() else null
            val pekareTmp = File(rot, "aktiv.tmp")
            skrivSynkat(pekareTmp, manifest.version.toByteArray())
            flyttaAtomart(pekareTmp, pekare)
            // Behåll aktivt och föregående paket, ta bort resten.
            paketRot.listFiles().orEmpty()
                .filter { it.name != manifest.version && it.name != foregaende }
                .forEach { it.deleteRecursively() }
            return Utfall.Installerat(Paket(manifest.version, slutlig, manifest), ms(start), redanAktivt = false)
        } catch (e: IOException) {
            return avvisa(tmp, "I/O-fel: ${e.message}")
        }
    }

    /** Växlar tillbaka till föregående version om den finns kvar. */
    fun aterstall(version: String): Boolean {
        val k = File(paketRot, version)
        if (!File(k, "manifest.json").isFile) return false
        val pekareTmp = File(rot, "aktiv.tmp")
        skrivSynkat(pekareTmp, version.toByteArray())
        flyttaAtomart(pekareTmp, pekare)
        return true
    }

    private fun avvisa(tmp: File, skal: String): Utfall.Avvisat {
        tmp.deleteRecursively()
        return Utfall.Avvisat(skal)
    }

    private fun ms(start: Long) = (System.nanoTime() - start) / 1_000_000

    private fun kopiera(inn: InputStream, mal: File): Pair<Long, String> {
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileOutputStream(mal).use { ut ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = inn.read(buf)
                if (n < 0) break
                md.update(buf, 0, n); ut.write(buf, 0, n); total += n
            }
            ut.flush(); ut.channel.force(true)
        }
        return total to md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun skrivSynkat(f: File, data: ByteArray) {
        FileOutputStream(f).use { it.write(data); it.flush(); it.channel.force(true) }
    }

    private fun flyttaAtomart(fran: File, till: File) {
        try {
            Files.move(fran.toPath(), till.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            throw IOException("filsystemet kan inte byta namn atomärt; installationen avbryts", e)
        }
    }
}
