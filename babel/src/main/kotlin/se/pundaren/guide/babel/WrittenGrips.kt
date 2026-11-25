package se.pundaren.guide.babel

import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.api.Mechanism
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.api.Verification
import se.pundaren.guide.archive.api.Visibility
import java.security.MessageDigest

/**
 * Nyskrivna repliker för de grepp som saknar rader i arkivet, så att alla sexton går att spela.
 *
 * De är byggarens, skrivna efter ägarbeslutet 2026-11-21 kl. 19.41 (byggaren får skriva Babel-Stoners
 * rader). De ligger här och inte i arkivet, eftersom bara ägaren kan godkänna en arkivrad: på skärmen
 * står de som GENERERAD med id som börjar på GEN, och ingen av dem utger sig för att vara ett citat.
 *
 * Varje replik ska visa sitt grepp så tydligt att det går att namnge, och fungera som svar på vilken
 * fråga som helst. Rikard är här mytologins syndabock (satirisk kanon), inte en återgiven person.
 */
object WrittenGrips {
    const val ID_PREFIX = "GEN-"
    const val AUTHOR = "Claude (byggaren)"

    private val texts: Map<String, List<String>> = mapOf(
        "BS-01" to listOf(
            "Det intressanta är inte frågan. Det intressanta är vilken sorts människa som ställer den.",
            "Innan vi går in på saken behöver vi tala om varför just du alltid måste veta saker.",
            "En trygg person hade inte behövt fråga. Vill du prata om det?",
        ),
        "BS-02" to listOf(
            "Det som hände är en sak. Hur du reagerade på det är det egentliga problemet.",
            "Jag hade kunnat svara, om du inte hade frågat i det tonfallet.",
            "Skadan uppstod först när du lade märke till den. Dessförinnan fungerade allt.",
        ),
        "BS-08" to listOf(
            "Bra. Då är vi överens om att börja om. Vad var det du undrade, från början?",
            "Efter noggrant övervägande landar jag i din första fråga, som jag föreslår att du ställer igen.",
            "Vi har nu gått ett helt varv. Enligt protokollet återupptas ärendet vid punkt ett.",
        ),
        "BS-09" to listOf(
            "Det där får du ta med Rikard. Han var inte här, och det är just det som är så typiskt.",
            "Utredningen är inte klar, men Rikard är redan underrättad om sin skuld.",
            "Ingen vet säkert vad som hände. Däremot vet alla att det var Rikard.",
        ),
        "BS-10" to listOf(
            "En vecka har sju dagar. Jag hoppas att det reder ut saken.",
            "En kvadrat har fyra sidor. Det gäller oavsett vad du frågade om.",
            "Efter måndag kommer tisdag. Du frågade inte om det, men svaret är korrekt, och det är mer än man kan säga om frågan.",
        ),
        "BS-11" to listOf(
            "Svaret är 4,7 kilo. Jag är medveten om att du inte frågade efter en vikt.",
            "Ungefär 312 hektar, avrundat nedåt. Noggrannare än så blir det inte i den här enheten.",
            "Elva decibel. Räkna om själv om du föredrar kronor.",
        ),
        "BS-12" to listOf(
            "Det där har jag redan svarat på, i en tidslinje där du lyssnade.",
            "Frågan är besvarad och arkiverad i ett angränsande universum. Vänd dig dit för en kopia.",
            "Vi hade det här samtalet i går, fast i en annan verklighet. Där var du nöjd med svaret.",
        ),
        "BS-13" to listOf(
            "Har du handduken med dig? Då är saken löst. Har du den inte, så förklarar det allt.",
            "Handduken låg framme hela tiden. Mer bevisning än så tänker jag inte lägga fram.",
            "Jag hade svarat, men jag letade efter handduken. Det får räcka som både förklaring och åtgärd.",
        ),
        "BS-14" to listOf(
            "Frågan är för enkel för att besvaras enkelt. Den har därför remitterats till ett svart hål för beredning.",
            "Ju klarare du uttrycker dig, desto fler galaxer berörs. Vi är nu uppe i fyra.",
            "Det där var nästan begripligt. För säkerhets skull har tyngdlagen kallats till samråd.",
        ),
        "BS-16" to listOf(
            "Enligt en naturlag som gäller fram till punkten i den här meningen har du redan fått svar.",
            "I just den här meningen går tiden baklänges, och därför ställde du aldrig frågan.",
            "Tillfälligt gäller att den som frågar också är den som vet. Lagen upphörde nyss.",
        ),
    )

    /** Raderna i arkivets form, så att spelet och skärmen kan visa id, klass och upphov som för allt annat. */
    val lines: List<ArchiveEntry> = texts.flatMap { (ruleId, replies) ->
        replies.mapIndexed { i, text ->
            ArchiveEntry(
                id = "$ID_PREFIX$ruleId-${i + 1}",
                originClass = OriginClass.SYNTHETIC_SATIRE,
                subtype = "byggarens replik",
                kind = EntryKind.EXAMPLE,
                text = text,
                textSha256 = sha256(text),
                language = "sv",
                verification = Verification.UNKNOWN,
                // Inte godkänd rad för rad av ägaren. Bara ägaren kan sätta APPROVED (04, 1.3).
                approval = Approval.PENDING,
                visibility = Visibility.OPEN,
                author = AUTHOR,
                sourceRef = "babel/WrittenGrips.kt#$ruleId",
                sourceNote = "Nyskriven för spelet 2026-11-25. Inte ett återgivet samtal och inte ett citat.",
                derivedFrom = ruleId,
                legacyId = null,
                category = "spelreplik",
                mechanisms = if (ruleId == "BS-09") setOf(Mechanism.M5, Mechanism.M6) else setOf(Mechanism.M6),
                tags = setOf("rule:$ruleId"),
            )
        }
    }

    fun linesFor(ruleId: String): List<ArchiveEntry> = lines.filter { it.derivedFrom == ruleId }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
