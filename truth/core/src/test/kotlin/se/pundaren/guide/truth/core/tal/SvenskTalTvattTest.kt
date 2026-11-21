package se.pundaren.guide.truth.core.tal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvenskTalTvattTest {
    private fun t(in_: String) = SvenskTalTvatt.tvatta(in_)

    @Test fun `dubbelt mellanslag och saknat på i startbeskedet`() {
        assertEquals("Kör söderut på Hantverkargatan. Därefter sväng vänster in på Slaggatan.",
            t("Kör söderut  Hantverkargatan. Därefter Sväng vänster in på Slaggatan."))
        assertEquals("Kör västerut på Dalagatan. Därefter, i 100 meter, sväng höger in på Badstugatan.",
            t("Kör västerut  Dalagatan. Därefter, i 100 meter, Sväng höger in på Badstugatan."))
        assertEquals("Fortsätt i 700 meter.", t("Fortsätt  i  700 meter."))
    }

    @Test fun `liten bokstav mitt i meningen bara för instruktionsord`() {
        assertEquals("Om 500 meter, sväng höger in på Bergevägen.", t("Om 500 meter, Sväng höger in på Bergevägen."))
        assertEquals("Sväng höger. Därefter din destination kommer att vara till vänster.",
            t("Sväng höger. Därefter Din destination kommer att vara till vänster."))
        assertEquals("Om 50 meter, fortsätt.", t("Om 50 meter, Fortsätt."))
        // Gatunamn efter "Därefter" rörs inte (inte i vitlistan)
        assertEquals("Därefter Moravägen.", t("Därefter Moravägen."))
        // Meningens första ord rörs inte
        assertEquals("Sväng vänster.", t("Sväng vänster."))
    }

    @Test fun `decimalkomma före enhet`() {
        assertEquals("Fortsätt i 1,5 kilometer.", t("Fortsätt i 1.5 kilometer."))
        assertEquals("Fortsätt i 2,5 kilometer.", t("Fortsätt i 2.5 kilometer."))
        assertEquals("Fortsätt i 11 kilometer.", t("Fortsätt i 11 kilometer."))
    }

    @Test fun `europaväg utan mellanslag`() {
        assertEquals("Fortsätt på E45 i 11 kilometer.", t("Fortsätt på E 45 i 11 kilometer."))
        assertEquals("Sväng vänster in på E45, Inlandsvägen.", t("Sväng vänster in på E 45, Inlandsvägen."))
    }

    @Test fun `vägnummer efter gatunamn tas bort`() {
        assertEquals("Sväng höger in på Badstugatan. Därefter kör in i rondellen och ta den 2:a avfarten in på Älvgatan.",
            t("Sväng höger in på Badstugatan, 70.04. Därefter Kör in i rondellen och ta den 2:a avfarten in på Älvgatan."))
        assertEquals("Kör ut ur rondellen in på Kaplansgatan.", t("Kör ut ur rondellen in på Kaplansgatan, W 1000."))
        assertEquals("Kör norrut på Dalgatan.", t("Kör norrut på Dalgatan, 70."))
        assertEquals("Fortsätt på Inlandsvägen i 10 kilometer.", t("Fortsätt på Inlandsvägen, 84 i 10 kilometer."))
        assertEquals("Kör ut ur rondellen in på Norra Järnvägsgatan. Därefter sväng vänster in på Tingsgatan.",
            t("Kör ut ur rondellen in på Norra Järnvägsgatan, 84. Därefter Sväng vänster in på Tingsgatan."))
        assertEquals("Kör ut ur rondellen in på 70, Riksvägen.".let { t(it) }, "Kör ut ur rondellen in på väg 70, Riksvägen.")
    }

    @Test fun `ensamt vägnummer blir väg N som på skylten`() {
        assertEquals("Fortsätt på väg 1000 i 2 kilometer.", t("Fortsätt på W 1000 i 2 kilometer."))
        assertEquals("Sväng höger in på väg 70.", t("Sväng höger in på 70."))
        assertEquals("Fortsätt på väg 84 i 17 kilometer.", t("Fortsätt på 84 i 17 kilometer."))
        assertEquals("Kör in i rondellen och ta den 2:a avfarten in på väg 83, väg 84.",
            t("Kör in i rondellen och ta den 2:a avfarten in på 83, 84."))
        assertEquals("Fortsätt på E45, väg 70 i 1,5 kilometer.", t("Fortsätt på E 45, 70 i 1.5 kilometer."))
        assertEquals("Kör in i rondellen och ta den 2:a avfarten in på E45, väg 70.",
            t("Kör in i rondellen och ta den 2:a avfarten in på E 45, 70."))
        assertEquals("Håll vänster för att ta E45, väg 70.", t("Håll vänster för att ta E 45, 70."))
    }

    @Test fun `ensamt delnummer med punkt tas bort med sitt på`() {
        assertEquals("Sväng höger.", t("Sväng höger in på 70.04."))
        assertEquals("Fortsätt i 2 kilometer.", t("Fortsätt på 70.04 i 2 kilometer."))
    }

    @Test fun `avstånd 2-a avfart och orter rörs inte`() {
        assertEquals("Kör ut ur rondellen mot Enköping, Rättvik.", t("Kör ut ur rondellen mot Enköping, Rättvik."))
        assertEquals("Om 1 kilometer, kör in i rondellen och ta den 2:a avfarten in på Inlandsvägen.",
            t("Om 1 kilometer, Kör in i rondellen och ta den 2:a avfarten in på Inlandsvägen."))
    }

    private val riktningsord = listOf("höger", "vänster", "norrut", "söderut", "österut", "västerut", "rondellen", "avfarten")

    /** Alla 270 unika besked i etapp2-ruttproven: riktningsord, avståndsvärden och gatunamn bevaras. */
    @Test fun `etapp2-beskeden bevarar riktning avstånd och gatunamn`() {
        val katalog = File(System.getProperty("guide.repoRoot"), "docs/baseline/verification/etapp2-testutdata/ruttprov")
        val filer = katalog.listFiles { f -> f.name.endsWith(".osrm.json") }.orEmpty()
        assertTrue("ruttproven hittas", filer.size >= 11)
        val besked = filer.flatMap { f ->
            Regex("\"announcement\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(f.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        assertTrue(besked.size > 100)
        val avstand = Regex("""(\d+(?:[.,]\d+)?) (kilometer|meter)""")
        val gatunamn = Regex("""\p{Lu}\p{Ll}+(?:gatan|vägen|väg|rondellen|backen|stigen)\b""")
        for (b in besked) {
            val u = t(b)
            for (r in riktningsord) assertEquals("$r i: $b", Regex("\\b$r\\b").findAll(b.lowercase()).count(), Regex("\\b$r\\b").findAll(u.lowercase()).count())
            assertEquals("avstånd i: $b", avstand.findAll(b).map { it.value.replace('.', ',') }.toList(), avstand.findAll(u).map { it.value }.toList())
            assertEquals("gatunamn i: $b", gatunamn.findAll(b).map { it.value }.toList(), gatunamn.findAll(u).map { it.value }.toList())
            assertFalse("dubbelt mellanslag: $u", u.contains("  "))
            assertFalse("W-nummer kvar: $u", Regex("""\b[A-Z]{1,2} \d{3,4}\b""").containsMatchIn(u))
            assertFalse("delnummer kvar: $u", Regex("""\b\d{1,3}\.\d{2}\b""").containsMatchIn(u))
            assertFalse("nummer efter komma kvar: $u", Regex(""", \d""").containsMatchIn(u))
            assertFalse("Sväng mitt i: $u", Regex("""(Därefter|meter,|kilometer,) Sväng""").containsMatchIn(u))
        }
    }
}
