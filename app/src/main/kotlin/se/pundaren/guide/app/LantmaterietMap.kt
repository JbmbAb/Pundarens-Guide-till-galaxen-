package se.pundaren.guide.app

import android.util.Base64
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil

/**
 * Lantmäteriets Topografisk webbkarta Visning, översiktlig: färdiga kartbilder över nätet, licens CC0.
 * Används bara i kartläget Lantmäteriet, och bara när ägaren har lagt in sitt systemkonto under Reglage.
 *
 * Tjänsten kräver Basic-inloggning med ett systemkonto från Geotorget. Kontot läggs bara på anrop till
 * Lantmäteriets egen karttjänst över https, aldrig på något annat, och skrivs aldrig i loggen.
 */
object LantmaterietMap {
    const val HOST = "maps.lantmateriet.se"

    /** Kartbilder i Web Mercator, 256 bildpunkter. Raden (y) kommer före kolumnen (x) i adressen. */
    const val TILES = "https://$HOST/open/topowebb-ccby/v1/wmts/1.0.0/topowebb/default/3857/{z}/{y}/{x}.png"

    /** Den översiktliga tjänsten slutar på nivå 14. Närmare än så förstoras bilderna. */
    const val MAX_ZOOM = 14f
    const val ATTRIBUTION = "© Lantmäteriet"

    /** Hur mycket kartbilder som får ligga kvar på enheten, så att det man har tittat på finns utan nät. */
    const val CACHE_BYTES = 500L * 1024 * 1024

    @Volatile private var authorization: String? = null

    val hasAccount: Boolean get() = authorization != null

    fun setAccount(user: String?, password: String?) {
        authorization = if (user.isNullOrBlank() || password.isNullOrBlank()) null
        else "Basic " + Base64.encodeToString("$user:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /** Ger kartbiblioteket en nätklient som lägger till inloggningen. Anropas en gång, innan kartan skapas. */
    fun install() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val account = authorization
            val ours = account != null && request.url.isHttps && request.url.host == HOST
            chain.proceed(if (ours) request.newBuilder().header("Authorization", account!!).build() else request)
        }.build()
        HttpRequestUtil.setOkHttpClient(client)
    }
}
