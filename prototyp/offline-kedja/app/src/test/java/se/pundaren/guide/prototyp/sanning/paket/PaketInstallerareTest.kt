package se.pundaren.guide.prototyp.sanning.paket

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PaketInstallerareTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun kalla(version: String, filer: Map<String, ByteArray>, forfalska: Map<String, ByteArray> = emptyMap(),
                      avbrytVid: String? = null): PaketInstallerare.Kalla {
        val m = JSONObject().put("format", PaketInstallerare.FORMAT).put("version", version)
            .put("filer", JSONArray(filer.map { (n, b) -> JSONObject().put("namn", n).put("storlek", b.size.toLong()).put("sha256", sha(b)) }))
        return PaketInstallerare.Kalla { namn ->
            if (namn == avbrytVid) object : InputStream() {
                var n = 0
                override fun read(): Int = if (n++ < 10) 1 else throw IOException("simulerat avbrott")
            } else if (namn == "manifest.json") m.toString().byteInputStream()
            else (forfalska[namn] ?: filer.getValue(namn)).inputStream()
        }
    }

    private val v1 = mapOf("karta.pmtiles" to ByteArray(300_000) { (it % 251).toByte() }, "valhalla_tiles.tar" to ByteArray(70_000) { 7 })
    private val v2 = mapOf("karta.pmtiles" to ByteArray(310_000) { (it % 13).toByte() }, "valhalla_tiles.tar" to ByteArray(71_000) { 9 })

    @Test fun `installerar och pekar ut aktivt paket`() {
        val inst = PaketInstallerare(tmp.root)
        val u = inst.installera(kalla("orsa-1", v1))
        assertTrue(u is PaketInstallerare.Utfall.Installerat)
        val a = inst.aktivt()!!
        assertEquals("orsa-1", a.version)
        assertTrue(a.fil("karta.pmtiles").readBytes().contentEquals(v1.getValue("karta.pmtiles")))
        assertEquals(listOf("aktiv", "paket"), tmp.root.list()!!.sorted())
    }

    @Test fun `fel kontrollsumma avvisas och det gamla paketet ligger kvar`() {
        val inst = PaketInstallerare(tmp.root)
        inst.installera(kalla("orsa-1", v1))
        val u = inst.installera(kalla("orsa-2", v2, forfalska = mapOf("valhalla_tiles.tar" to ByteArray(71_000) { 8 })))
        assertTrue(u is PaketInstallerare.Utfall.Avvisat)
        assertTrue((u as PaketInstallerare.Utfall.Avvisat).skal.contains("SHA-256"))
        assertEquals("orsa-1", inst.aktivt()!!.version)
        assertFalse(File(tmp.root, "paket/orsa-2").exists())
        assertTrue(tmp.root.list()!!.none { it.startsWith("tmp-") })
    }

    @Test fun `fel storlek avvisas`() {
        val inst = PaketInstallerare(tmp.root)
        val u = inst.installera(kalla("orsa-1", v1, forfalska = mapOf("karta.pmtiles" to ByteArray(10))))
        assertTrue(u is PaketInstallerare.Utfall.Avvisat)
        assertNull(inst.aktivt())
    }

    @Test fun `avbrott mitt i kopieringen lämnar det gamla paketet aktivt och städas vid nästa start`() {
        val inst = PaketInstallerare(tmp.root)
        inst.installera(kalla("orsa-1", v1))
        val u = inst.installera(kalla("orsa-2", v2, avbrytVid = "karta.pmtiles"))
        assertTrue(u is PaketInstallerare.Utfall.Avvisat)
        assertEquals("orsa-1", inst.aktivt()!!.version)
        // Simulera en process som dog innan den hann städa: en kvarlämnad tmp-katalog.
        File(tmp.root, "tmp-orsa-2-123").apply { mkdirs(); File(this, "karta.pmtiles").writeBytes(ByteArray(5)) }
        File(tmp.root, "aktiv.tmp").writeText("orsa-2")
        assertEquals("orsa-1", inst.aktivt()!!.version)
        assertEquals(2, inst.stada())
        assertEquals("orsa-1", inst.aktivt()!!.version)
    }

    @Test fun `ny version byter pekaren, föregående sparas för återställning, äldre tas bort`() {
        val inst = PaketInstallerare(tmp.root)
        inst.installera(kalla("orsa-1", v1))
        inst.installera(kalla("orsa-2", v2))
        assertEquals("orsa-2", inst.aktivt()!!.version)
        assertTrue(File(tmp.root, "paket/orsa-1").isDirectory)
        inst.installera(kalla("orsa-3", v1))
        assertFalse(File(tmp.root, "paket/orsa-1").exists())
        assertTrue(inst.aterstall("orsa-2"))
        assertEquals("orsa-2", inst.aktivt()!!.version)
    }

    @Test fun `samma version installeras inte om`() {
        val inst = PaketInstallerare(tmp.root)
        inst.installera(kalla("orsa-1", v1))
        val u = inst.installera(kalla("orsa-1", v1)) as PaketInstallerare.Utfall.Installerat
        assertTrue(u.redanAktivt)
    }

    @Test fun `farliga namn i manifestet avvisas`() {
        for (namn in listOf("../aktiv", "a/b", ".dold", "manifest.json")) {
            val m = JSONObject().put("format", PaketInstallerare.FORMAT).put("version", "v")
                .put("filer", JSONArray().put(JSONObject().put("namn", namn).put("storlek", 1).put("sha256", "0".repeat(64))))
            val u = PaketInstallerare(tmp.newFolder()).installera { m.toString().byteInputStream() }
            assertTrue(namn, u is PaketInstallerare.Utfall.Avvisat)
        }
    }

    @Test fun `trasig pekare ger inget aktivt paket`() {
        val inst = PaketInstallerare(tmp.root)
        inst.installera(kalla("orsa-1", v1))
        File(tmp.root, "paket/orsa-1/karta.pmtiles").writeBytes(ByteArray(3)) // avkortad fil
        assertNull(inst.aktivt())
    }
}
