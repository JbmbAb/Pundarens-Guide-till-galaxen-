package se.pundaren.guide.prototyp.sanning.paket

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Atomär installation av offlinepaketet (karta.pmtiles + valhalla_tiles.tar + manifest.json).
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
    data class Manifest(val format: String, val version: String, val filer: List<Fil>, val ratext: String)
    data class Paket(val version: String, val katalog: File, val manifest: Manifest) {
        fun fil(namn: String) = File(katalog, namn)
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

        fun lasManifest(text: String): Manifest {
            val j = JSONObject(text)
            val format = j.getString("format")
            require(format == FORMAT) { "okänt paketformat: $format" }
            val version = j.getString("version")
            require(TILLATET_NAMN.matches(version) && version != "." && version != "..") { "ogiltig version: $version" }
            val arr = j.getJSONArray("filer")
            val filer = (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                val namn = f.getString("namn")
                require(TILLATET_NAMN.matches(namn) && namn != "manifest.json" && !namn.startsWith(".")) { "ogiltigt filnamn: $namn" }
                val sha = f.getString("sha256").lowercase()
                require(Regex("^[0-9a-f]{64}$").matches(sha)) { "ogiltig sha256 för $namn" }
                Fil(namn, f.getLong("storlek"), sha)
            }
            require(filer.isNotEmpty()) { "manifestet saknar filer" }
            require(filer.map { it.namn }.toSet().size == filer.size) { "dubbla filnamn i manifestet" }
            return Manifest(format, version, filer, text)
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
