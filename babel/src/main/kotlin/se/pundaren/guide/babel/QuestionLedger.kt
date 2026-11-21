package se.pundaren.guide.babel

/**
 * Babel-Stoner, Analysläget: **frågebokföring** (byggplanen; 02 B-2 till B-5).
 *
 * Du klistrar in ett samtal. Bokföringen listar varje fråga och raden där den ställdes, vad nästa
 * replik från den andra parten innehöll, om frågan ställdes igen, och var en replik saknar gemensamma
 * ord med det som sades närmast före. Samma regler gäller båda parter.
 *
 * Det här är resultatklass ett av tre i 02 B-4: **deterministiskt konstaterade textförhållanden**.
 * Varje fynd pekar på radnummer som går att kontrollera. Ingen språkmodell är inkopplad (`:llm:api`
 * finns inte än), så klass två, modellens osäkra tolkning, är alltid tom. Om en fråga *fick svar* är
 * en tolkning och bedöms därför inte: det står [Answer.NOT_ASSESSED].
 *
 * Bokföringen beskriver text, inte människor (02 B-3, RR-09): inga motiv, inga diagnoser och inga
 * omdömen. Den är en ren funktion: ingenting sparas och ingenting lämnar enheten (02 B-5).
 */
object QuestionLedger {

    /** En rad i det inklistrade samtalet. [number] är radnumret i den inklistrade texten, från 1. */
    data class Line(val number: Int, val speaker: String, val text: String)

    enum class Answer {
        /** Kräver tolkning av innehållet. Görs inte utan språkmodell. */
        NOT_ASSESSED,
    }

    /** Vad som går att konstatera om en fråga utan att tolka den. */
    data class QuestionEntry(
        val line: Int,
        val speaker: String,
        val question: String,
        /** Raden med nästa replik från en annan talare, eller null om ingen sådan kom. */
        val nextReplyLine: Int?,
        val nextReplySpeaker: String?,
        /** Nästa replik innehåller själv ett frågetecken. */
        val nextReplyHasQuestion: Boolean,
        /** Innehållsord som frågan och nästa replik har gemensamt. */
        val sharedWords: List<String>,
        /** Rader där samma talare senare ställde en fråga med till stor del samma innehållsord. */
        val askedAgainAt: List<Int>,
        val answer: Answer = Answer.NOT_ASSESSED,
    )

    /** En replik utan gemensamma innehållsord med de [window] repliker som kom närmast före. */
    data class WordBreak(val line: Int, val speaker: String, val window: Int)

    data class SpeakerTally(val speaker: String, val lines: Int, val questions: Int, val questionsMetByQuestion: Int, val questionsAskedAgain: Int)

    data class Ledger(
        val lines: List<Line>,
        val questions: List<QuestionEntry>,
        val wordBreaks: List<WordBreak>,
        val tallies: List<SpeakerTally>,
    ) {
        /** Resultatklassen enligt 02 B-4. Den enda den här bokföringen producerar. */
        val resultClass: String get() = RESULT_CLASS
    }

    const val RESULT_CLASS = "Deterministiskt konstaterat textförhållande"
    const val NOT_ASSESSED_NOTE = "Om frågan fick svar är inte bedömt. Det kräver tolkning, och ingen språkmodell är inkopplad."
    const val UNKNOWN_SPEAKER = "Okänd"
    private const val WINDOW = 3
    private const val REPEAT_SHARE = 0.5

    private val speakerPrefix = Regex("""^\s*(?:\[[^\]]{1,40}]\s*)?(?:\d{1,2}[:.]\d{2}(?::\d{2})?\s*[-–]?\s*)?([^\s:\[\]][^:\[\]]{0,39}?)\s*:\s+(\S.*)$""")
    private val wordPattern = Regex("""[\p{L}\p{Nd}]+""")

    /** Vanliga funktionsord som inte säger något om vad en mening handlar om. */
    private val stopWords = setOf(
        "och", "att", "det", "som", "en", "ett", "den", "de", "dem", "på", "är", "var", "av", "för", "med", "till", "om", "har", "hade", "inte",
        "jag", "du", "han", "hon", "vi", "ni", "man", "mig", "dig", "sig", "min", "din", "sin", "mitt", "ditt", "vår", "er", "oss", "men", "så",
        "när", "nu", "då", "där", "här", "vad", "vem", "vilka", "vilken", "varför", "hur", "kan", "ska", "skulle", "vill", "bara", "också", "eller",
        "från", "över", "under", "efter", "innan", "utan", "sedan", "mycket", "lite", "väl", "ju", "nog", "ändå", "faktiskt", "alltså", "liksom",
        "blir", "blev", "vara", "varit", "gör", "gjorde", "gjort", "göra", "säger", "sa", "sade", "sagt", "säga", "får", "fick", "måste", "kunde",
        "detta", "dessa", "denna", "något", "någon", "några", "ingen", "inget", "alla", "allt", "varje", "hela", "själv", "igen", "redan", "aldrig", "alltid",
    )

    /** Delar upp den inklistrade texten i repliker. En rad utan "Namn:" hör till talaren närmast före. */
    fun parse(text: String): List<Line> {
        val out = ArrayList<Line>()
        var speaker = UNKNOWN_SPEAKER
        text.lines().forEachIndexed { index, raw ->
            if (raw.isBlank()) return@forEachIndexed
            val match = speakerPrefix.matchEntire(raw)
            if (match != null) {
                speaker = match.groupValues[1].trim()
                out += Line(index + 1, speaker, match.groupValues[2].trim())
            } else {
                out += Line(index + 1, speaker, raw.trim())
            }
        }
        return out
    }

    /** Innehållsorden i en text: ord med minst fyra tecken som inte är funktionsord. */
    fun contentWords(text: String): Set<String> =
        wordPattern.findAll(text.lowercase()).map { it.value }.filter { it.length >= 4 && it !in stopWords }.toSet()

    /** Meningarna i en replik som slutar med frågetecken. */
    fun questionsIn(text: String): List<String> =
        Regex("""[^.!?]*\?+""").findAll(text).map { it.value.trim() }.filter { it.length > 1 }.toList()

    fun analyze(text: String): Ledger {
        val lines = parse(text)
        val questions = ArrayList<QuestionEntry>()
        lines.forEachIndexed { i, line ->
            for (question in questionsIn(line.text)) {
                val words = contentWords(question)
                val reply = lines.drop(i + 1).firstOrNull { it.speaker != line.speaker }
                val again = lines.drop(i + 1).filter { later ->
                    later.speaker == line.speaker && questionsIn(later.text).any { q ->
                        val w = contentWords(q)
                        words.isNotEmpty() && w.isNotEmpty() && words.intersect(w).size >= REPEAT_SHARE * minOf(words.size, w.size)
                    }
                }.map { it.number }
                questions += QuestionEntry(
                    line = line.number, speaker = line.speaker, question = question,
                    nextReplyLine = reply?.number, nextReplySpeaker = reply?.speaker,
                    nextReplyHasQuestion = reply != null && '?' in reply.text,
                    sharedWords = reply?.let { words.intersect(contentWords(it.text)).sorted() }.orEmpty(),
                    askedAgainAt = again,
                )
            }
        }
        val breaks = lines.mapIndexedNotNull { i, line ->
            if (i < WINDOW) return@mapIndexedNotNull null
            val own = contentWords(line.text)
            if (own.isEmpty()) return@mapIndexedNotNull null
            val before = lines.subList(i - WINDOW, i).flatMap { contentWords(it.text) }.toSet()
            if (before.isNotEmpty() && own.intersect(before).isEmpty()) WordBreak(line.number, line.speaker, WINDOW) else null
        }
        val tallies = lines.map { it.speaker }.distinct().map { speaker ->
            val own = questions.filter { it.speaker == speaker }
            SpeakerTally(
                speaker = speaker, lines = lines.count { it.speaker == speaker }, questions = own.size,
                questionsMetByQuestion = own.count { it.nextReplyHasQuestion }, questionsAskedAgain = own.count { it.askedAgainAt.isNotEmpty() },
            )
        }
        return Ledger(lines, questions, breaks, tallies)
    }

    /** Bokföringen som text. Bara konstateranden med radnummer. */
    fun render(ledger: Ledger): String = buildString {
        appendLine("FRÅGEBOKFÖRING")
        appendLine("Resultatklass: $RESULT_CLASS. Varje fynd pekar på radnummer i den inklistrade texten.")
        appendLine(NOT_ASSESSED_NOTE)
        appendLine()
        if (ledger.questions.isEmpty()) appendLine("Ingen mening i texten slutar med frågetecken.")
        for (q in ledger.questions) {
            appendLine("Rad ${q.line}, ${q.speaker}: «${q.question}»")
            when {
                q.nextReplyLine == null -> appendLine("  Ingen senare replik från någon annan finns i texten.")
                else -> {
                    appendLine("  Nästa replik från någon annan: rad ${q.nextReplyLine}, ${q.nextReplySpeaker}.")
                    appendLine(
                        if (q.sharedWords.isEmpty()) "  Den repliken har inga innehållsord gemensamt med frågan."
                        else "  Gemensamma innehållsord: ${q.sharedWords.joinToString(", ")}.",
                    )
                    if (q.nextReplyHasQuestion) appendLine("  Den repliken innehåller själv ett frågetecken.")
                }
            }
            if (q.askedAgainAt.isNotEmpty()) appendLine("  Samma talare ställde en fråga med till stor del samma ord igen på rad ${q.askedAgainAt.joinToString(", ")}.")
        }
        if (ledger.wordBreaks.isNotEmpty()) {
            appendLine()
            appendLine("Repliker utan gemensamma innehållsord med de tre närmast föregående:")
            ledger.wordBreaks.forEach { appendLine("  Rad ${it.line}, ${it.speaker}.") }
        }
        if (ledger.tallies.isNotEmpty()) {
            appendLine()
            appendLine("Per talare (samma regler för alla):")
            ledger.tallies.forEach {
                appendLine("  ${it.speaker}: ${it.lines} repliker, ${it.questions} frågor, ${it.questionsMetByQuestion} möttes av en replik med frågetecken, ${it.questionsAskedAgain} ställdes igen.")
            }
        }
    }
}
