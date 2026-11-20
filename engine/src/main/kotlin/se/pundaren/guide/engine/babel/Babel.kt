package se.pundaren.guide.engine.babel

import se.pundaren.guide.engine.model.BabelExample
import se.pundaren.guide.engine.model.BabelMode
import se.pundaren.guide.engine.model.EngineOutput
import se.pundaren.guide.engine.model.GalacticAnalysis
import se.pundaren.guide.engine.model.GlitchEffect
import se.pundaren.guide.engine.model.LoadedArchive
import se.pundaren.guide.engine.model.PresentedQuote
import se.pundaren.guide.engine.model.ProbabilityDrive
import se.pundaren.guide.engine.model.Provenance
import se.pundaren.guide.engine.model.QuotedPair
import se.pundaren.guide.engine.model.ReverseAnalysis
import se.pundaren.guide.engine.model.SatiricalLabel
import kotlin.random.Random

private const val ANALYSIS_NOTE = "Bedömningarna är satiriska etiketter, inte vetenskapliga mätvärden."
private const val RIKARD_VERDICT = "Rikard har godkänt protokollet."
private const val SUPPORT_REFERRAL = "Vid akut fara, ring 112. För råd om vård, kontakta 1177."
private val threats = listOf(
    Regex("""(?i)döda dig"""),
    Regex("""(?i)slå ihjäl"""),
    Regex("""(?i)ta livet av"""),
    Regex("""(?i)våldta"""),
    Regex("""(?i)misshandla"""),
)
private val negation = Regex("""(?i)\b(inte|aldrig|icke)\b""")
private val stopwords = setOf(
    "och", "att", "det", "den", "detta", "en", "ett", "jag", "du", "ni", "vi", "som", "på", "för",
    "med", "om", "är", "var", "inte", "de", "har", "hade", "ska", "skulle", "till", "av", "så",
    "men", "eller", "när", "vad", "vem", "hur", "varför", "vilken", "inte", "aldrig", "mig", "dig",
    "sig", "han", "hon", "hen", "man", "kan", "får", "från", "utan", "bara", "också", "vara",
)

fun translateBabel(
    text: String,
    modeId: String?,
    archive: LoadedArchive,
    drive: ProbabilityDrive,
    random: Random,
): EngineOutput {
    val mode = BabelMode.fromId(modeId)
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return babelOutput(
            commentary = "Skriv en replik först. Tystnad är inte ett citat.",
            mode = mode,
            analysis = analysis(mode, 0, false, "Ingen sakfråga att besvara."),
            drive = drive,
            quotes = emptyList(),
            sourceId = null,
            sourceProvenance = Provenance.KONSTRUERAD,
        )
    }
    val example = matchExample(trimmed, archive)
    if (example != null && example.provenance != Provenance.ORIGINAL) {
        val reply = exampleReply(example)
        if (reply != null) {
            return babelOutput(
                commentary = reply,
                mode = mode,
                analysis = analysisFromExample(example, mode),
                drive = drive,
                quotes = emptyList(),
                sourceId = example.id,
                sourceProvenance = example.provenance,
            )
        }
    }
    val rules = mode.ruleIds.mapNotNull { id -> archive.rules.firstOrNull { it.id == id } }
    val rule = if (rules.isEmpty()) null else rules[random.nextInt(rules.size)]
    val echo = "«${trimmed.replace(Regex("\\s+"), " ").take(240)}»"
    val function = rule?.function ?: "Undvik sakfrågan."
    val wounded = if (mode == BabelMode.SARADE_HJALTEN) {
        archive.patterns.firstOrNull { !it.babelStonerRule.isNullOrBlank() }?.babelStonerRule
    } else {
        null
    }
    val frames = listOf(
        "$echo är noterat. $function Det besvarar inte frågan.",
        "Frågan var $echo. $function Ursprungsfrågan står kvar.",
        "$function När texten säger $echo flyttas samtalet, och svaret gör det inte.",
    )
    val cosmic = if (mode == BabelMode.KOSMISK) {
        "\n\nSvaret är 42, i fel enhet. $RIKARD_VERDICT"
    } else {
        ""
    }
    val extra = wounded?.let { "\n\n$it" }.orEmpty()
    val commentary = frames[random.nextInt(frames.size)] + extra + cosmic
    val related = (rule?.relatedQuoteIds.orEmpty() + rule?.thematicQuoteIds.orEmpty())
        .mapNotNull { archive.byId(it) }
        .filter { it.provenance == Provenance.ORIGINAL }
        .distinctBy { it.id }
        .map { PresentedQuote(it.id, it.text, Provenance.ORIGINAL, it.title) }
        .filter { quote -> !commentary.contains(quote.text) }
    return babelOutput(
        commentary = commentary,
        mode = mode,
        analysis = analysis(mode, 0, false, rules.joinToString(" ") { it.function }.ifBlank {
            "Ursprunglig fråga fortfarande obesvarad."
        }),
        drive = drive,
        quotes = related,
        sourceId = rule?.id,
        sourceProvenance = Provenance.KONSTRUERAD,
    )
}

fun analyzeTranscript(text: String, archive: LoadedArchive): ReverseAnalysis {
    val disclaimer = "Inget av detta är en diagnos och inget motiv tillskrivs någon person."
    if (threats.any { it.containsMatchIn(text) }) {
        return ReverseAnalysis(
            satireSuppressed = true,
            supportReferral = SUPPORT_REFERRAL,
            summary = "Texten innehåller tecken på hot eller våld. Den satiriska tonen är avstängd.",
            unanswered = emptyList(),
            contradictions = emptyList(),
            patternNotes = emptyList(),
            disclaimer = disclaimer,
        )
    }
    val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    val unanswered = lines.mapIndexedNotNull { index, line ->
        if (!line.contains('?')) return@mapIndexedNotNull null
        val words = contentWords(line)
        if (words.isEmpty()) return@mapIndexedNotNull null
        val following = lines.drop(index + 1).take(4)
        val answered = following.any { follow -> contentWords(follow).any { it in words } }
        if (answered) null else line
    }
    val contradictions = buildList {
        for (i in lines.indices) {
            for (j in i + 1 until lines.size) {
                val a = lines[i]
                val b = lines[j]
                if (a.length < 12 || b.length < 12) continue
                val shared = contentWords(a).intersect(contentWords(b)).any { it.length > 4 }
                val aNeg = negation.containsMatchIn(a)
                val bNeg = negation.containsMatchIn(b)
                if (shared && aNeg != bNeg) add(QuotedPair(a, b))
            }
        }
    }.distinct()
    val notes = patternNotes(text, archive)
    val summary = if (unanswered.isEmpty() && contradictions.isEmpty() && notes.isEmpty()) {
        "Inga tydliga mönster av den här typen hittades i texten. Det betyder inte att texten är entydig, bara att motorn inte såg dem. $disclaimer"
    } else {
        "Texten innehåller ${unanswered.size} frågor som verkar obesvarade och ${contradictions.size} möjliga motsägelser. $disclaimer"
    }
    return ReverseAnalysis(
        satireSuppressed = false,
        supportReferral = null,
        summary = summary,
        unanswered = unanswered,
        contradictions = contradictions,
        patternNotes = notes,
        disclaimer = disclaimer,
    )
}

fun dontPanicOutput(
    archive: LoadedArchive,
    drive: ProbabilityDrive,
    random: Random,
    transmissionNumber: Int,
): EngineOutput {
    val original = archive.originals().let { quotes ->
        if (quotes.isEmpty()) null else quotes[random.nextInt(quotes.size)]
    }
    val pair = archive.valuePairs.let { pairs ->
        if (pairs.isEmpty()) null else pairs[random.nextInt(pairs.size)]
    }
    val paradox = if (pair != null) {
        "Paradox, konstruerad: ${pair.a} och ${pair.b} byter plats. Det här är inte ett citat."
    } else {
        "Paradox, konstruerad: stabilitet och galenskap byter namn. Det här är inte ett citat."
    }
    val derail = if (drive.chaos >= 0.99f) "\n\nDet har jag aldrig sagt." else ""
    val rikard = random.nextFloat() < drive.rikardFrequency || drive.chaos >= 0.99f
    val rikardLine = if (rikard) "\n\nRikardstörning, konstruerad: signalen är ostadig." else ""
    val missing = if (original == null) {
        "\n\nInget originalcitat finns i arkivet, så inget personligt citat visas."
    } else {
        ""
    }
    val commentary = "Observation: någon bad om att inte få panik.\n\n$paradox\n\n" +
        "Verkligheten: mat, toalett och vila är fortfarande praktiska saker.$derail$rikardLine$missing"
    val presented = original?.let {
        listOf(PresentedQuote(it.id, it.text, Provenance.ORIGINAL, it.title))
    }.orEmpty().filter { !commentary.contains(it.text) }
    return EngineOutput(
        commentary = commentary,
        facts = null,
        usedQuoteIds = presented.map { it.id },
        isGeneratedVariation = true,
        rikardInvolved = rikard,
        glitch = if (rikard) GlitchEffect(400) else null,
        quotes = presented,
        transmissionNumber = transmissionNumber,
        sourceProvenance = Provenance.KONSTRUERAD,
    )
}

private fun babelOutput(
    commentary: String,
    mode: BabelMode,
    analysis: GalacticAnalysis,
    drive: ProbabilityDrive,
    quotes: List<PresentedQuote>,
    sourceId: String?,
    sourceProvenance: Provenance,
): EngineOutput = EngineOutput(
    commentary = commentary,
    facts = null,
    usedQuoteIds = quotes.map { it.id },
    isGeneratedVariation = true,
    rikardInvolved = true,
    glitch = if (drive.rikardFrequency >= 0.85f || mode == BabelMode.KOSMISK) GlitchEffect(400) else null,
    quotes = quotes,
    analysis = analysis,
    modeName = mode.title,
    sourceId = sourceId,
    sourceProvenance = sourceProvenance,
)

private fun analysis(mode: BabelMode, percent: Int, answered: Boolean, prose: String): GalacticAnalysis {
    val mechanismProse = mode.mechanisms.joinToString(" ") { it.prose }
    return GalacticAnalysis(
        answeredPercent = percent,
        mechanisms = mode.mechanisms,
        originalQuestionAnswered = answered,
        rikardVerdict = RIKARD_VERDICT,
        prose = listOf(mechanismProse, prose, "Ursprunglig fråga fortfarande obesvarad.", RIKARD_VERDICT)
            .filter { it.isNotBlank() }
            .joinToString(" "),
        labels = listOf(
            SatiricalLabel("Ursprunglig fråga besvarad", if (answered) "Ja" else "Nej"),
            SatiricalLabel("Besvarad sakfråga", "$percent %"),
            SatiricalLabel("Galaktisk absurditet", mode.title),
        ),
        note = ANALYSIS_NOTE,
    )
}

private fun analysisFromExample(example: BabelExample, mode: BabelMode): GalacticAnalysis {
    val answeredLabel = example.labels.firstOrNull { it.label.contains("besvarad", ignoreCase = true) }?.value
    val percent = when (answeredLabel?.lowercase()) {
        "ja" -> 100
        "delvis" -> 40
        else -> 0
    }
    val answered = percent == 100
    val prose = example.result ?: example.labels.joinToString(". ") { "${it.label}: ${it.value}" }
    return GalacticAnalysis(
        answeredPercent = percent,
        mechanisms = mode.mechanisms,
        originalQuestionAnswered = answered,
        rikardVerdict = RIKARD_VERDICT,
        prose = prose,
        labels = example.labels.ifEmpty {
            listOf(SatiricalLabel("Ursprunglig fråga besvarad", if (answered) "Ja" else "Nej"))
        },
        note = example.analysisNote ?: ANALYSIS_NOTE,
    )
}

private fun matchExample(text: String, archive: LoadedArchive): BabelExample? {
    val normalized = normalize(text)
    return archive.examples.firstOrNull { example ->
        if (example.provenance == Provenance.ORIGINAL) return@firstOrNull false
        val candidates = buildList {
            example.original?.let { add(it) }
            example.turns.filter { it.role == "fråga" }.forEach { add(it.text) }
        }
        candidates.any { normalize(it) == normalized }
    }
}

private fun exampleReply(example: BabelExample): String? = when {
    !example.response.isNullOrBlank() -> example.response
    example.turns.any { it.role == "svar" } -> example.turns.filter { it.role == "svar" }.joinToString("\n\n") { it.text }
    else -> null
}

private fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

private fun contentWords(line: String): Set<String> = line
    .lowercase()
    .split(Regex("""[^\p{L}\p{N}]+"""))
    .filter { it.length > 3 && it !in stopwords }
    .toSet()

private fun patternNotes(text: String, archive: LoadedArchive): List<String> {
    val hits = buildList {
        if (Regex("""(?i)inte så jag (sa|menade)|förnekar du""").containsMatchIn(text)) add("PAT-01")
        if (Regex("""(?i)tonfall|din ton|hur du säger|upprepade frågan""").containsMatchIn(text)) add("PAT-03")
        if (Regex("""(?i)angrepp|kränkt|sårad""").containsMatchIn(text)) add("PAT-02")
        if (Regex("""(?i)löfte""").containsMatchIn(text) && Regex("""(?i)litade|tillit""").containsMatchIn(text)) {
            add("PAT-05")
        }
        if (Regex("""(?i)skäms|skam""").containsMatchIn(text)) add("PAT-04")
    }
    val fromArchive = hits.mapNotNull { id -> archive.patterns.firstOrNull { it.id == id } }
        .filter { !it.isEstablishedPsychologicalFact }
        .map { pattern ->
            "Kan tolkas som mönstret «${pattern.title}». Regelbiblioteket markerar det som hypotes, inte som ett faktum om en person."
        }
    val generic = buildList {
        if (Regex("""(?i)tonfall|din ton""").containsMatchIn(text)) {
            add("Kan tolkas som ett möjligt ämnesbyte. Inget motiv tillskrivs någon person.")
        }
        if (Regex("""(?i)varför tar du upp|att du frågar|du minns""").containsMatchIn(text)) {
            add("Kan tolkas som möjlig ansvarsförskjutning. Inget motiv tillskrivs någon person.")
        }
        if (Regex("""(?i)på grund av dig|du fick mig|ditt fel""").containsMatchIn(text)) {
            add("Kan tolkas som möjlig omvänd skuld. Inget motiv tillskrivs någon person.")
        }
    }
    return (fromArchive + generic).distinct()
}
