package se.pundaren.guide.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LantmaterietCheckTest {
    /** Svarar med en statuskod per anrop. 200 ger en tom kropp, allt annat blir ett fel som i den riktiga porten. */
    private class StatusHttp(private val status: (url: String, authorization: String?) -> Int, private val tokenBody: String? = null) : HttpPort {
        val calls = mutableListOf<Pair<String, String?>>()
        override fun get(url: String, headers: Map<String, String>): String {
            calls += url to headers["Authorization"]
            val code = status(url, headers["Authorization"])
            if (code !in 200..299) throw OnlineException(code, "svarade $code")
            return ""
        }
        override fun post(url: String, headers: Map<String, String>, body: String): String {
            calls += url to headers["Authorization"]
            return tokenBody ?: throw OnlineException(401, "svarade 401")
        }
    }

    private fun service(name: String) = Lantmateriet.services.single { it.name == name }

    @Test fun `listan har visnings- och direkttjänsterna, alla över https hos Lantmäteriet, och ingen nedladdning`() {
        assertEquals(13, Lantmateriet.services.size)
        assertEquals(13, Lantmateriet.services.map { it.name }.toSet().size)
        assertTrue(Lantmateriet.services.none { "edladdning" in it.name })
        Lantmateriet.services.forEach { s ->
            val host = if (s.access == LmAccess.VIEW) "https://maps.lantmateriet.se/" else "https://api.lantmateriet.se/"
            assertTrue(s.name, s.testUrl.startsWith(host))
        }
        assertEquals(4, Lantmateriet.services.count { it.access == LmAccess.VIEW })
        assertEquals(9, Lantmateriet.services.count { it.access == LmAccess.API })
    }

    @Test fun `en visningstjänst prövas bara med systemkontot`() {
        val map = service("Topografisk webbkarta Visning, översiktlig")
        assertEquals(LmResult.NO_LOGIN, LantmaterietCheck(StatusHttp({ _, _ -> 200 })).check(map, account = null, bearer = "Bearer xyz").status)
        val http = StatusHttp({ _, auth -> if (auth == "Basic abc") 200 else 401 })
        val result = LantmaterietCheck(http).check(map, account = "Basic abc", bearer = "Bearer xyz")
        assertEquals(LmResult(map, 200, Lantmateriet.VIA_ACCOUNT), result)
        assertEquals(1, http.calls.size)
    }

    @Test fun `en api-tjänst prövas först med systemkontot och sedan med nyckeln`() {
        val height = service("Markhöjd Direkt")
        val http = StatusHttp({ _, auth -> if (auth == "Bearer xyz") 405 else 401 })
        val result = LantmaterietCheck(http).check(height, account = "Basic abc", bearer = "Bearer xyz")
        // 405 kan bara komma efter godkänd inloggning: kontrollfrågan var inte en giltig fråga, men den kom fram.
        assertTrue(result.accepted)
        assertEquals(Lantmateriet.VIA_KEY, result.via)
        assertEquals(listOf("Basic abc", "Bearer xyz"), http.calls.map { it.second })

        val denied = LantmaterietCheck(StatusHttp({ _, _ -> 403 })).check(height, account = "Basic abc", bearer = null)
        assertFalse(denied.accepted)
        assertEquals(403, denied.status)
        assertFalse(LantmaterietCheck(StatusHttp({ _, _ -> 0 })).check(height, account = "Basic abc", bearer = null).accepted)
    }

    @Test fun `nyckelparet byts mot ett åtkomstbevis, och ett nekat par ger inget`() {
        val http = StatusHttp({ _, _ -> 200 }, tokenBody = """{"access_token":"abc123","token_type":"Bearer","expires_in":3600}""")
        assertEquals("Bearer abc123", LantmaterietCheck(http).token("nyckel", "hemlighet"))
        assertEquals(Lantmateriet.TOKEN_URL to Lantmateriet.basic("nyckel", "hemlighet"), http.calls.single())
        assertNull(LantmaterietCheck(StatusHttp({ _, _ -> 200 })).token("nyckel", "fel"))
        assertNull(LantmaterietCheck(StatusHttp({ _, _ -> 200 }, tokenBody = "<html>")).token("nyckel", "hemlighet"))
    }
}
