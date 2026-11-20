package se.pundaren.guide.engine

import se.pundaren.guide.engine.archive.ArchiveLoader
import se.pundaren.guide.engine.babel.analyzeTranscript
import se.pundaren.guide.engine.babel.dontPanicOutput
import se.pundaren.guide.engine.babel.translateBabel
import se.pundaren.guide.engine.commentary.CommentaryGenerator
import se.pundaren.guide.engine.commentary.SessionMemory
import se.pundaren.guide.engine.model.ArchiveBundle
import se.pundaren.guide.engine.model.EngineInput
import se.pundaren.guide.engine.model.EngineOutput
import se.pundaren.guide.engine.model.FactPayload
import se.pundaren.guide.engine.model.InputKind
import se.pundaren.guide.engine.model.LoadedArchive
import se.pundaren.guide.engine.model.ProbabilityDrive
import se.pundaren.guide.engine.model.Provenance
import se.pundaren.guide.engine.model.ReverseAnalysis
import kotlin.random.Random

interface IllogicalEngine {
    fun load(bundle: ArchiveBundle)
    fun archive(): LoadedArchive
    fun respond(input: EngineInput, drive: ProbabilityDrive): EngineOutput
    fun commentaryFor(facts: FactPayload, drive: ProbabilityDrive): EngineOutput
    fun translate(text: String, modeId: String?, drive: ProbabilityDrive): EngineOutput
    fun analyze(transcript: String): ReverseAnalysis
}

class DefaultIllogicalEngine(
    private val commentary: CommentaryGenerator = CommentaryGenerator(),
) : IllogicalEngine {
    private var loaded: LoadedArchive = LoadedArchive.EMPTY
    private val memory = SessionMemory()
    private var transmission = 41

    override fun load(bundle: ArchiveBundle) {
        loaded = ArchiveLoader.load(bundle)
        memory.clear()
    }

    override fun archive(): LoadedArchive = loaded

    override fun respond(input: EngineInput, drive: ProbabilityDrive): EngineOutput = when (input.kind) {
        InputKind.NAVIGATION -> commentaryFor(input.facts ?: FactPayload(), drive)
        InputKind.BABEL -> translate(input.text, input.modeId, drive)
        InputKind.DONT_PANIC -> dontPanic(drive)
        InputKind.OBSERVATION -> observe(input.text, drive)
    }

    override fun commentaryFor(facts: FactPayload, drive: ProbabilityDrive): EngineOutput {
        val draft = commentary.generate(facts, loaded, drive, memory, random(drive))
        memory.remember(draft.commentary)
        return EngineOutput(
            commentary = draft.commentary,
            facts = draft.facts,
            usedQuoteIds = listOfNotNull(draft.quote?.id),
            isGeneratedVariation = true,
            rikardInvolved = draft.rikardInvolved,
            glitch = draft.glitch,
            quotes = listOfNotNull(draft.quote),
            sourceId = draft.sourceId,
            sourceProvenance = draft.sourceProvenance,
        )
    }

    override fun translate(text: String, modeId: String?, drive: ProbabilityDrive): EngineOutput =
        translateBabel(text, modeId, loaded, drive, random(drive))

    override fun analyze(transcript: String): ReverseAnalysis = analyzeTranscript(transcript, loaded)

    fun dontPanic(drive: ProbabilityDrive): EngineOutput {
        transmission += 1
        return dontPanicOutput(loaded, drive, random(drive), transmission)
    }

    private fun observe(text: String, drive: ProbabilityDrive): EngineOutput {
        val safe = text.trim().ifBlank { "något hände, och det registrerades." }
        val commentary = "Observation: $safe\n\n" +
            "Paradox, konstruerad: det som sägs och det som gäller behöver inte vara samma sak. " +
            "Det här är inte ett citat.\n\n" +
            "Verkligheten: inget avstånd, ingen gata och ingen plats har ändrats av den här meningen."
        return EngineOutput(
            commentary = commentary,
            facts = null,
            usedQuoteIds = emptyList(),
            isGeneratedVariation = true,
            rikardInvolved = drive.chaos >= 0.99f,
            glitch = null,
            sourceProvenance = Provenance.KONSTRUERAD,
        )
    }

    private fun random(drive: ProbabilityDrive): Random = Random(drive.seed ?: System.nanoTime())
}
