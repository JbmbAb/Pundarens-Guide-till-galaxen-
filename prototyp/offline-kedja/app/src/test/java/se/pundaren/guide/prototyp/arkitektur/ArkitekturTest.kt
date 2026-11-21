package se.pundaren.guide.prototyp.arkitektur

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Källkodsprov: sanningslagret står ensamt, inget nätverk, inga Google-API:er. */
class ArkitekturTest {
    private val kallor = File("src/main").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()

    @Test fun `källorna hittas`() = assertTrue(kallor.size >= 5)

    @Test fun `ingen Illogical Engine eller tramslager i prototypen`() {
        val traffar = kallor.filter { f -> f.readText().let { it.contains("se.pundaren.guide.engine") || it.contains("se.pundaren.guide.babel") } }
        assertEquals(emptyList<File>(), traffar)
    }

    @Test fun `inga Google-paket importeras`() {
        val monster = Regex("""com\.google\.(android\.gms|firebase|crypto\.tink|android\.libraries\.maps|maps)""")
        assertEquals(emptyList<String>(), kallor.filter { monster.containsMatchIn(it.readText()) }.map { it.path })
    }

    @Test fun `navigeringsrösten i sanning tal är ren Kotlin utan Android, UI eller Guiden`() {
        val tal = kallor.filter { it.path.contains("/sanning/tal/") }
        assertTrue("sanning/tal finns", tal.size >= 2)
        val forbjudet = Regex("""^import (android\.|androidx\.|se\.pundaren\.guide\.prototyp\.ui|se\.pundaren\.guide\.(engine|babel))""", RegexOption.MULTILINE)
        assertEquals(emptyList<String>(), tal.filter { forbjudet.containsMatchIn(it.readText()) }.map { it.path })
    }

    @Test fun `talmotorn binds bara via TTS_SERVICE och ingen INTERNET-behörighet läggs till`() {
        val m = File("src/main/AndroidManifest.xml").readText()
        assertTrue(m.contains("android.intent.action.TTS_SERVICE"))
        assertFalse(m.contains("com.google.android.tts"))
    }

    @Test fun `manifestet tar bort INTERNET`() {
        val m = File("src/main/AndroidManifest.xml").readText()
        assertTrue(Regex("""android.permission.INTERNET"\s+tools:node="remove"""").containsMatchIn(m))
        assertFalse(Regex("""<uses-permission android:name="android.permission.INTERNET"\s*/>""").containsMatchIn(m))
    }

    @Test fun `ingen nätadress i stilen`() {
        val stil = File("src/main/assets/stil/mork.json").readText()
        assertFalse(stil.contains("http://") || stil.contains("https://"))
        assertFalse(stil.contains("\"glyphs\"") || stil.contains("\"sprite\""))
    }
}
