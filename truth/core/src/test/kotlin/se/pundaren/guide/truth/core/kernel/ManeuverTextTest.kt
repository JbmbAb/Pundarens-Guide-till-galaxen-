package se.pundaren.guide.truth.core.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverTextTest {
    @Test fun `beskedet i fast form med avstånd`() {
        assertEquals("Vänster om 300 m", ManeuverText.line("turn", "left", 300.0))
        assertEquals("Höger om 1,2 km", ManeuverText.line("turn", "right", 1_240.0))
        assertEquals("Rakt fram om 11 km", ManeuverText.line("continue", "straight", 11_000.0))
        assertEquals("Vänster", ManeuverText.line("turn", "left", 8.0))
        assertEquals("Framme om 100 m", ManeuverText.line("arrive", null, 100.0))
        assertEquals("Fortsätt", ManeuverText.line(null, null, null))
    }

    @Test fun `alla riktningar och manövertyper har text och pil`() {
        assertEquals("Svagt vänster", ManeuverText.headline("turn", "slight left"))
        assertEquals("Skarpt höger", ManeuverText.headline("turn", "sharp right"))
        assertEquals("U-sväng", ManeuverText.headline("turn", "u turn"))
        assertEquals("Rondell", ManeuverText.headline("roundabout", "right"))
        assertEquals("Ut ur rondellen", ManeuverText.headline("exit roundabout", "right"))
        assertEquals("Håll höger", ManeuverText.headline("fork", "slight right"))
        assertEquals("Avfart höger", ManeuverText.headline("off ramp", "right"))
        assertEquals("Vägen tar slut, vänster", ManeuverText.headline("end of road", "left"))
        assertEquals("Starta", ManeuverText.headline("depart", "straight"))
        assertEquals(ManeuverText.Arrow.LEFT, ManeuverText.arrow("turn", "left"))
        assertEquals(ManeuverText.Arrow.SHARP_RIGHT, ManeuverText.arrow("new name", "sharp right"))
        assertEquals(ManeuverText.Arrow.ROUNDABOUT, ManeuverText.arrow("rotary", "left"))
        assertEquals(ManeuverText.Arrow.ARRIVE, ManeuverText.arrow("arrive", "left"))
        assertEquals(ManeuverText.Arrow.U_TURN, ManeuverText.arrow("continue", "u turn"))
        assertEquals(ManeuverText.Arrow.STRAIGHT, ManeuverText.arrow("notification", null))
    }

    /** Höger får aldrig bli vänster: varje modifierare med sida ger rätt ord och rätt pil. */
    @Test fun `sidan byts aldrig`() {
        for (type in listOf("turn", "fork", "on ramp", "off ramp", "merge", "end of road", "new name", "continue")) {
            for (m in listOf("left", "slight left", "sharp left")) {
                assertTrue("$type/$m", ManeuverText.headline(type, m).lowercase().contains("vänster"))
                assertFalse("$type/$m", ManeuverText.headline(type, m).lowercase().contains("höger"))
                assertTrue("$type/$m", ManeuverText.arrow(type, m).name.contains("LEFT"))
            }
            for (m in listOf("right", "slight right", "sharp right")) {
                assertTrue("$type/$m", ManeuverText.headline(type, m).lowercase().contains("höger"))
                assertFalse("$type/$m", ManeuverText.headline(type, m).lowercase().contains("vänster"))
                assertTrue("$type/$m", ManeuverText.arrow(type, m).name.contains("RIGHT"))
            }
        }
    }
}
