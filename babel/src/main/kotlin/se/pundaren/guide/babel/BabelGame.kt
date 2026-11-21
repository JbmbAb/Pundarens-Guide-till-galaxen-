package se.pundaren.guide.babel

import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.ArchiveReader
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.api.OriginClass

/**
 * Babel-Stoner som spel (byggplanen; 02 B-1): du ställer en fråga, Babel-Stoner slingrar sig med ett
 * grepp i taget, och du vinner när du namnger greppet och får tillbaka din fråga. Poängen är antalet
 * repliker tills frågan är återställd.
 *
 * **Bara godkänt material.** Ett grepp är en godkänd regel (BS-01–BS-16). Det kan bara spelas om det
 * har minst en godkänd arkivrad knuten till sig, för byggaren skriver inga egna repliker. Raderna
 * kommer i dag ur reglernas `relatedQuoteIds`; godkända exempel märkta `rule:<regel-id>` används
 * också när sådana finns. Rader som återger en verklig person (Rikard) används aldrig.
 * [unplayable] visar vilka grepp som saknar rader och väntar på ägarens godkända exempel.
 *
 * Deterministiskt: samma fråga, motståndare och frö ger samma spel. Greppen stannar här (02 B-6,
 * RR-13): klassen har inget beroende på navigatorn, motorn eller SafetyGate.
 */
class BabelGame(archive: ArchiveReader) {

    /**
     * De fem motståndarna är de fem lägena i ägarens PRD (avsnitt 5.3). Vilka grepp som hör till
     * vilken motståndare är ett förslag där också; fördelningen här är ett kandidatvärde som ägaren
     * får justera. Kosmisk totalförvirring använder alla grepp.
     */
    enum class Opponent(val title: String, internal val ruleIds: Set<String>?) {
        RESPONSIBILITY_SHIFTER("Ansvarsförskjutaren", setOf("BS-02", "BS-04", "BS-05", "BS-09")),
        ETERNAL_CIRCLE("Den eviga cirkeln", setOf("BS-08", "BS-10", "BS-12", "BS-15")),
        REALITY_AUDITOR("Verklighetsrevisorn", setOf("BS-03", "BS-07", "BS-11", "BS-16")),
        WOUNDED_HERO("Den sårade hjälten", setOf("BS-01", "BS-06", "BS-13", "BS-14")),
        COSMIC_CONFUSION("Kosmisk totalförvirring", null),
        ;

        companion object {
            const val MAPPING_STATUS = "KANDIDAT_OFRYST"
        }
    }

    /** Ett grepp som går att spela: regeln och de godkända rader som visar det. */
    data class Grip(val rule: ArchiveEntry, val lines: List<ArchiveEntry>)

    private val rules: List<ArchiveEntry> = archive.all()
        .filter { it.kind == EntryKind.RULE && it.originClass == OriginClass.DERIVED_RULE && it.approval == Approval.APPROVED }
        .sortedBy { it.id }

    private val usableLines: Map<String, ArchiveEntry> = archive.all()
        .filter {
            it.approval == Approval.APPROVED && "real_person_excerpt" !in it.tags && "archive_only" !in it.tags &&
                (it.kind == EntryKind.QUOTE || it.kind == EntryKind.EXAMPLE)
        }
        .associateBy { it.id }

    /** Spelbara grepp per regel-id. */
    val grips: Map<String, Grip> = rules.mapNotNull { rule ->
        val quoted = rule.tags.filter { it.startsWith("quote:") }.mapNotNull { usableLines[it.removePrefix("quote:")] }
        val tagged = usableLines.values.filter { "rule:${rule.id}" in it.tags }
        val lines = (quoted + tagged).distinctBy { it.id }.sortedBy { it.id }
        if (lines.isEmpty()) null else rule.id to Grip(rule, lines)
    }.toMap()

    /** Godkända grepp som inte går att spela än, eftersom ingen godkänd rad är knuten till dem. */
    val unplayable: List<ArchiveEntry> = rules.filter { it.id !in grips }

    fun gripsOf(opponent: Opponent): List<Grip> =
        grips.values.filter { opponent.ruleIds == null || it.rule.id in opponent.ruleIds }.sortedBy { it.rule.id }

    /** Spelets läge. Oföränderligt: varje drag ger ett nytt läge. */
    data class Round(
        val question: String,
        val opponent: Opponent,
        val seed: Long,
        /** Antal repliker Babel-Stoner har gett hittills. Det är poängen när spelet är vunnet; lägre är bättre. */
        val replies: Int,
        val wrongGuesses: Int,
        /** Greppet som används i den replik som visas nu. Skärmen visar det först när spelet är vunnet. */
        val grip: Grip,
        /** Den godkända arkivraden som visas. */
        val line: ArchiveEntry,
        /** Svarsalternativen: regler att välja bland, i fast ordning. Det rätta är alltid med. */
        val options: List<ArchiveEntry>,
        val won: Boolean,
    ) {
        /** Etiketten gör att ingen kan ta repliken för annat än satir (02 B-4). */
        val label: String get() = "Satir"
    }

    /** Börjar ett spel. Null om motståndaren inte har något spelbart grepp, eller om frågan är tom. */
    fun start(question: String, opponent: Opponent, seed: Long): Round? {
        val text = question.trim()
        if (text.isEmpty()) return null
        val available = gripsOf(opponent)
        if (available.isEmpty()) return null
        return turn(text, opponent, seed, replies = 1, wrongGuesses = 0, available = available, previous = null)
    }

    /**
     * Spelaren namnger greppet. Rätt: frågan är återställd och spelet vunnet. Fel: Babel-Stoner
     * slingrar sig vidare med nästa grepp.
     */
    fun guess(round: Round, ruleId: String): Round {
        if (round.won) return round
        if (ruleId == round.grip.rule.id) return round.copy(won = true)
        return turn(
            round.question, round.opponent, round.seed, round.replies + 1, round.wrongGuesses + 1,
            gripsOf(round.opponent), previous = round.grip.rule.id,
        )
    }

    private fun turn(
        question: String, opponent: Opponent, seed: Long, replies: Int, wrongGuesses: Int, available: List<Grip>, previous: String?,
    ): Round {
        // Samma grepp två gånger i rad vore inget nytt att gissa på, om det finns fler att välja bland.
        val pool = available.filter { it.rule.id != previous }.ifEmpty { available }
        val h = mix(seed, replies.toLong(), question)
        val grip = pool[Math.floorMod(h, pool.size.toLong()).toInt()]
        val line = grip.lines[Math.floorMod(h ushr 17, grip.lines.size.toLong()).toInt()]
        // Tre felaktiga alternativ bland alla godkända regler, plus det rätta, i regelordning.
        val others = rules.filter { it.id != grip.rule.id }
        val wrong = LinkedHashSet<ArchiveEntry>()
        var k = h
        while (wrong.size < minOf(OPTIONS - 1, others.size)) {
            k = k * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            wrong += others[Math.floorMod(k ushr 20, others.size.toLong()).toInt()]
        }
        return Round(question, opponent, seed, replies, wrongGuesses, grip, line, (wrong + grip.rule).sortedBy { it.id }, won = false)
    }

    private fun mix(seed: Long, turn: Long, text: String): Long {
        var h = seed xor 0x2545F4914F6CDD1DL
        h = h * 1_000_003L + turn
        for (c in text.lowercase()) h = h * 1_000_003L + c.code
        h = (h xor (h ushr 30)) * -0x40a7b892e31b1a47L
        h = (h xor (h ushr 27)) * -0x6b2fb644ecceee15L
        return h xor (h ushr 31)
    }

    companion object {
        const val OPTIONS = 4
    }
}

/**
 * Allvar före satir (02 B-7): vid tecken på hot eller våld i det användaren skriver bryts satiren
 * och appen hänvisar till 112. Ordlistan är avsiktligt bred; ett falskt larm kostar bara ett skämt.
 * Vilka stödlinjer som ska stå här utöver nödnumret är ägarens beslut (E7-D3).
 */
object SeriousnessCheck {
    private val signs = listOf(
        "döda", "dödar", "dödshot", "mörda", "slå ihjäl", "slår mig", "slog mig", "slagit mig", "misshandl", "våldt", "hotar", "hotade", "hotat",
        "skada mig", "skadar mig", "rädd för honom", "rädd för henne", "rädd för mitt liv", "självmord", "ta mitt liv", "ta livet av", "vill inte leva",
    )

    const val MESSAGE = "Det här kan vara allvarligt. Babel-Stoner är satir och svarar inte på det. Vid fara: ring 112. Polisen når du annars på 114 14."

    fun isSerious(text: String): Boolean {
        val t = text.lowercase()
        return signs.any { it in t }
    }
}
