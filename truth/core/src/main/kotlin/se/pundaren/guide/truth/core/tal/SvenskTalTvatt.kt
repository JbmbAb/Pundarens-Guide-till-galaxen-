package se.pundaren.guide.truth.core.tal

/**
 * Sanningslagrets tvätt av Valhallas svenska talbesked (sv-SE) före uppläsning.
 * Ren Kotlin, inget Android, inget beroende till Guiden eller Illogical Engine.
 *
 * Regler (etapp2-protokollet §6, etapp3-tal-protokollet):
 *  1. Dubbla mellanslag blir ett. "Kör söderut  Hantverkargatan" (Valhallas startmall saknar "på")
 *     får "på" bara i exakt det mönstret: Kör/Gå/Cykla + väderstreck + dubbelt mellanslag.
 *  2. Stor bokstav mitt i meningen blir liten, bara för instruktionsord i en vitlista
 *     (Sväng, Kör, Håll, Fortsätt, Gå, Ta, Vänd, Din, Du …) efter "Därefter" eller efter
 *     "Om N meter," / "Därefter, i N meter,". Gatunamn rörs aldrig.
 *  3. Decimalpunkt före en längdenhet blir decimalkomma: "1.5 kilometer" -> "1,5 kilometer".
 *  4. Europavägar: "E 45" -> "E45".
 *  5. Vägnummer ur OSM:s ref-tagg som Valhalla hänger på efter gatunamnet
 *     ("Badstugatan, 70.04", "Kaplansgatan, W 1000", "Dalgatan, 70") tas bort; gatunamnet står kvar.
 *     Står vägnumret ensamt efter "på", "mot" eller "ta" blir länsvägar och riksvägar "väg N" (som på skylten,
 *     "W 1000" -> "väg 1000", "70" -> "väg 70"). Ett ensamt delnummer med punkt ("70.04") kan inte
 *     sägas säkert och tas bort tillsammans med "in på"/"på".
 * Riktningsord, avståndsvärden och gatunamn ändras aldrig utöver mellanslag och versaler.
 */
object SvenskTalTvatt {
    private const val ENHET = "(?:kilometer|meter|km|m)\\b"
    private const val LAN = "(?:AB|AC|BD|[CDEFGHIKMNOSTUWXYZ])"

    // En vägreferens. Ordningen spelar roll: europaväg, länsväg, delnummer, riksväg/primär länsväg.
    private const val REF =
        "(?:E \\d{1,2}(?!\\d)|E\\d{1,2}(?!\\d)|$LAN \\d{3,4}(?!\\d)|\\d{1,3}\\.\\d{1,2}(?!\\d)(?!\\.\\d)(?! $ENHET)|\\d{1,3}(?!\\d)(?!\\.\\d)(?!:)(?!,\\d)(?! $ENHET))"

    private val dubbelt = Regex(" {2,}")
    private val startUtanPa = Regex("""^(Kör|Gå|Cykla) (norrut|söderut|österut|västerut|nordost|nordväst|sydost|sydväst|nordöst|sydöst)  (?=\p{Lu})""")
    private val instruktionsord = listOf(
        "Sväng", "Kör", "Håll", "Fortsätt", "Gå", "Ta", "Vänd", "Stanna", "Cykla", "Åk", "Följ", "Din", "Du",
    ).joinToString("|")
    private val versalEfterDarefter = Regex("""(Därefter,? (?:i \d+(?:[.,]\d+)? $ENHET, )?)($instruktionsord)\b""")
    private val versalEfterOm = Regex("""(\bOm \d+(?:[.,]\d+)? $ENHET, )($instruktionsord)\b""")
    private val decimal = Regex("""(?<![\d.])(\d+)\.(\d+)(?= $ENHET)""")
    private val refEfterNamn = Regex("""(?<=\p{Ll}), $REF(?:, $REF)*(?=[.,!?]|\s|$)""")
    private val ensamRef = Regex("""(?<=\bpå |\bmot |\bta )($REF(?:, $REF)*)(?=[.,!?]|\s|$)""")
    private val ensamtDelnummer = Regex("""(?: in)? på väg \d{1,3}\.\d{1,2}(?=[.,!?]|\s|$)""")
    private val europavag = Regex("""\bE (\d{1,2})(?!\d)""")

    fun tvatta(text: String): String {
        var s = text.trim()
        s = startUtanPa.replace(s) { "${it.groupValues[1]} ${it.groupValues[2]} på " }
        s = dubbelt.replace(s, " ")
        s = refEfterNamn.replace(s, "")
        s = ensamRef.replace(s) { m -> m.groupValues[1].split(", ").joinToString(", ") { omRef(it) } }
        s = ensamtDelnummer.replace(s, "")
        s = europavag.replace(s) { "E${it.groupValues[1]}" }
        s = decimal.replace(s) { "${it.groupValues[1]},${it.groupValues[2]}" }
        s = versalEfterDarefter.replace(s) { it.groupValues[1] + it.groupValues[2].lowercase() }
        s = versalEfterOm.replace(s) { it.groupValues[1] + it.groupValues[2].lowercase() }
        s = dubbelt.replace(s, " ").replace(" .", ".").replace(" ,", ",").trim()
        return s
    }

    private fun omRef(ref: String): String = when {
        Regex("""^E ?\d{1,2}$""").matches(ref) -> ref.replace(" ", "")
        Regex("""^$LAN (\d{3,4})$""").matches(ref) -> "väg " + ref.substringAfter(' ')
        else -> "väg $ref" // riksväg/primär länsväg ("70") och delnummer ("70.04", tas bort efteråt)
    }
}
