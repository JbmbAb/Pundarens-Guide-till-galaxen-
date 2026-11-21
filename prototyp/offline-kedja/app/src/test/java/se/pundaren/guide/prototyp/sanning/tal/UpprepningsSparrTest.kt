package se.pundaren.guide.prototyp.sanning.tal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpprepningsSparrTest {
    private val besked = "Fortsätt på E45 i 11 kilometer."

    @Test fun `samma besked direkt efter omräkning stoppas`() {
        val s = UpprepningsSparr()
        assertTrue(s.slapp(besked))
        s.omrakning()
        assertFalse(s.slapp(besked))
    }

    @Test fun `tre omräkningar i rad som i etapp2-emulatorn ger ett enda besked`() {
        val s = UpprepningsSparr()
        assertTrue(s.slapp(besked))
        repeat(3) { s.omrakning(); assertFalse(s.slapp(besked)) }
    }

    @Test fun `upprepning utan omräkning släpps igenom`() {
        val s = UpprepningsSparr()
        assertTrue(s.slapp("Sväng vänster."))
        assertTrue(s.slapp("Sväng vänster."))
    }

    @Test fun `nytt besked efter omräkning släpps och nollställer spärren`() {
        val s = UpprepningsSparr()
        assertTrue(s.slapp(besked))
        s.omrakning()
        assertTrue(s.slapp("Sväng höger in på Moravägen."))
        assertTrue(s.slapp(besked)) // ingen ny omräkning: släpps
    }

    @Test fun `jämförelsen bryr sig inte om versaler och mellanslag`() {
        val s = UpprepningsSparr()
        assertTrue(s.slapp(besked))
        s.omrakning()
        assertFalse(s.slapp("  fortsätt på E45  i 11 kilometer. "))
    }

    @Test fun `ny tur nollställer`() {
        val s = UpprepningsSparr()
        assertTrue(s.slapp(besked))
        s.omrakning(); s.nollstall()
        assertTrue(s.slapp(besked))
    }
}
