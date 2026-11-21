package se.pundaren.guide.prototyp.sanning.rutt

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Appens fråga till valhalla-mobile ska vara densamma som verkstadens ruttprov (04-ruttprov.py). */
class RuttFragaTest {
    @Test fun `frågan matchar verkstadens`() {
        val j = JSONObject(ValhallaRuttkalla.ruttFraga(listOf(61.1201175 to 14.6222536, 61.2077132 to 14.5377551), "auto"))
        assertEquals("osrm", j.getString("format"))
        assertEquals("sv-SE", j.getString("language"))
        assertEquals(true, j.getBoolean("voice_instructions"))
        assertEquals(true, j.getBoolean("banner_instructions"))
        assertEquals("auto", j.getString("costing"))
        assertEquals(2, j.getJSONArray("locations").length())
        assertEquals(14.5377551, j.getJSONArray("locations").getJSONObject(1).getDouble("lon"), 0.0)
    }
}
