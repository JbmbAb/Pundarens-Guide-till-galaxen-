package se.pundaren.guide.truth.core.tal

/**
 * Stoppar ett talbesked som är identiskt med det senast upplästa när det kommer direkt efter en
 * omräkning (etapp2: "Fortsätt på E 45 i 11 kilometer" sades om efter varje omräkning).
 * Utan omräkning släpps upprepningar igenom; två lika besked i rad kan vara två olika manövrer.
 * Ren Kotlin, ingen tråd- eller Androidkoppling; anropas från huvudtråden.
 */
class UpprepningsSparr {
    private var senast: String? = null
    private var efterOmrakning = false

    /** Kallas när navigeringen börjar räkna om rutten. */
    fun omrakning() { efterOmrakning = true }

    /** Ny tur: glöm allt. */
    fun nollstall() { senast = null; efterOmrakning = false }

    /** true om beskedet ska läsas upp. Jämför efter tvätt, utan hänsyn till versaler och mellanslag. */
    fun slapp(besked: String): Boolean {
        val nyckel = besked.trim().lowercase().replace(Regex("\\s+"), " ")
        val stoppa = efterOmrakning && nyckel == senast
        if (!stoppa) {
            senast = nyckel
            efterOmrakning = false
        }
        return !stoppa
    }
}
