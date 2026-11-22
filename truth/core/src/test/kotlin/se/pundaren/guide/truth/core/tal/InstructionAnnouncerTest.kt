package se.pundaren.guide.truth.core.tal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.truth.api.InstructionVoice

class InstructionAnnouncerTest {
    private class FakeVoice : InstructionVoice {
        val spoken = mutableListOf<String>()
        var stops = 0
        override fun speakInstruction(text: String) { spoken += text }
        override fun stopInstruction() { stops++ }
        override val instructionSpeaking: StateFlow<Boolean> = MutableStateFlow(false)
    }

    private val voice = FakeVoice()
    private val log = mutableListOf<String>()
    private val announcer = InstructionAnnouncer(voice) { log += it }

    @Test fun `beskedet tvättas och läses upp, råtexten loggas`() {
        val outcome = announcer.announce("Kör söderut  Hantverkargatan. Därefter Sväng vänster in på Slaggatan.")
        assertEquals(InstructionAnnouncer.Outcome.Spoken("Kör söderut på Hantverkargatan. Därefter sväng vänster in på Slaggatan."), outcome)
        assertEquals(listOf("Kör söderut på Hantverkargatan. Därefter sväng vänster in på Slaggatan."), voice.spoken)
        assertEquals("TAL RÅTT: Kör söderut  Hantverkargatan. Därefter Sväng vänster in på Slaggatan.", log[0])
        assertEquals("TAL #1: Kör söderut på Hantverkargatan. Därefter sväng vänster in på Slaggatan.", log[1])
    }

    @Test fun `samma besked direkt efter omräkning läses inte upp igen`() {
        announcer.announce("Fortsätt på E 45 i 11 kilometer.")
        announcer.rerouteStarted()
        val outcome = announcer.announce("Fortsätt på E 45 i 11 kilometer.")
        assertEquals(InstructionAnnouncer.Outcome.RepeatSuppressed("Fortsätt på E45 i 11 kilometer."), outcome)
        assertEquals(1, voice.spoken.size)
        assertTrue(log.last().startsWith("TAL UPPREPNING stoppad efter omräkning"))
        announcer.newTrip()
        assertTrue(announcer.announce("Fortsätt på E 45 i 11 kilometer.") is InstructionAnnouncer.Outcome.Spoken)
    }

    @Test fun `avstängt ljud läser inget men loggar beskedet, tomt besked ger ingenting`() {
        announcer.muted = true
        assertEquals(InstructionAnnouncer.Outcome.Muted("Sväng höger."), announcer.announce("Sväng höger."))
        assertTrue(voice.spoken.isEmpty())
        assertEquals(InstructionAnnouncer.Outcome.Empty, announcer.announce("   "))
        announcer.muted = false
        announcer.announce("Sväng höger.")
        assertEquals(listOf("Sväng höger."), voice.spoken)
        announcer.stop()
        assertEquals(1, voice.stops)
    }
}
