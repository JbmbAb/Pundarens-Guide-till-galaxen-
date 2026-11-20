package se.pundaren.guide.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.engine.archive.ArchiveLoader
import se.pundaren.guide.engine.commentary.creativeIsSafe
import se.pundaren.guide.engine.commentary.instructionLabel
import se.pundaren.guide.engine.commentary.realitySentence
import se.pundaren.guide.engine.model.AntiNavIntensity
import se.pundaren.guide.engine.model.ArchiveBundle
import se.pundaren.guide.engine.model.BabelMode
import se.pundaren.guide.engine.model.FactPayload
import se.pundaren.guide.engine.model.InputKind
import se.pundaren.guide.engine.model.MapMode
import se.pundaren.guide.engine.model.ProbabilityDrive
import se.pundaren.guide.engine.model.Provenance
import se.pundaren.guide.engine.model.TurnDirection
import se.pundaren.guide.engine.model.parseProvenance
import java.io.File

class EngineTest {
    private val data = File(System.getProperty("guide.data"))

    @Test
    fun parsesRealArchiveWithoutAlteringOriginals() {
        val archive = loadReal()
        assertEquals(19, archive.originals().size)
        assertEquals(2, archive.quotes.count { it.provenance == Provenance.BEARBETAD })
        assertEquals(1, archive.quotes.count { it.provenance == Provenance.REDAKTIONELL })
        assertEquals(20, archive.quotes.count { it.provenance == Provenance.VARIATION })
        assertEquals(16, archive.rules.size)
        assertEquals(5, archive.patterns.size)
        assertEquals(12, archive.examples.size)
        assertEquals(13, archive.lexicon.size)
        assertEquals(20, archive.navigationComments.size)

        val trust = archive.byId("C-007")
        assertEquals(TRUST_QUOTE, trust?.text)
        assertEquals("orig-0001", trust?.legacyId)
        assertTrue(trust?.provenance?.isUsersOwnQuote == true)

        val sand = archive.byId("P-001")
        assertTrue(sand?.text?.contains("arlset") == true)
        assertNotEquals(sand?.text, sand?.normalizedText)
        assertTrue(sand?.normalizedNote?.isNotBlank() == true)

        val originalIds = archive.originals().map { it.id }.toSet()
        val originalTexts = archive.originals().map { it.text }.toSet()
        archive.quotes.filter { it.provenance == Provenance.VARIATION }.forEach { variation ->
            assertTrue(variation.originalId in originalIds)
            assertFalse(variation.provenance.isUsersOwnQuote)
            assertFalse(variation.text in originalTexts)
            assertEquals(false, variation.approved)
        }
        assertTrue(archive.patterns.all { !it.isEstablishedPsychologicalFact })
    }

    @Test
    fun acceptsProvenanceValuesAndIgnoresUnknownFields() {
        val json = """
            {
              "schemaVersion": 2,
              "future": {"kept": false},
              "valuePairs": [{"id": "x", "a": "a", "b": "b", "extra": 1}],
              "quotes": [
                {"id": "o1", "text": "Oförändrad.", "provenance": "original", "surprise": true},
                {"id": "b1", "text": "Bearbetad text.", "provenance": "bearbetad"},
                {"id": "r1", "text": "Redaktionell text.", "provenance": "redaktionell"},
                {"id": "u1", "text": "Okänd.", "provenance": "något-annat"}
              ],
              "variations": [
                {"id": "v1", "provenance": "variation", "text": "En variation.", "originalId": "o1", "extra": 1},
                {"id": "v2", "provenance": "variation", "text": "Föräldralös.", "originalId": "saknas"},
                {"id": "v3", "provenance": "konstruerad", "text": "Konstruerad.", "originalId": "o1"},
                {"id": "v4", "provenance": "förslag", "text": "Förslag.", "originalId": "o1"},
                {"id": "v5", "provenance": "variation", "text": "Utan förälder."}
              ],
              "originals": [
                {"id": "legacy", "type": "original", "text": "Äldre form.", "note": "okänd nyckel"}
              ]
            }
        """.trimIndent()
        val archive = ArchiveLoader.load(ArchiveBundle(quotesJson = json))
        assertEquals("Oförändrad.", archive.byId("o1")?.text)
        assertEquals(Provenance.BEARBETAD, archive.byId("b1")?.provenance)
        assertEquals(Provenance.REDAKTIONELL, archive.byId("r1")?.provenance)
        assertEquals(Provenance.UNSPECIFIED, archive.byId("u1")?.provenance)
        assertFalse(archive.byId("u1")?.provenance?.isUsersOwnQuote == true)
        assertEquals("En variation.", archive.byId("v1")?.text)
        assertEquals("Konstruerad.", archive.byId("v3")?.text)
        assertEquals(Provenance.FORSLAG, archive.byId("v4")?.provenance)
        assertNull(archive.byId("v2"))
        assertNull(archive.byId("v5"))
        assertEquals("Äldre form.", archive.byId("legacy")?.text)
        assertEquals(Provenance.ORIGINAL, archive.byId("legacy")?.provenance)
        assertEquals(Provenance.ORIGINAL, parseProvenance("original"))
        assertEquals(Provenance.VARIATION, parseProvenance("variation"))
    }

    @Test
    fun commentaryKeepsFactsAndAgreesWithTheManeuver() {
        val engine = realEngine()
        val directions = listOf(
            FactPayload(distanceMeters = 400, direction = TurnDirection.LEFT, streetName = "Sveavägen"),
            FactPayload(distanceMeters = 200, direction = TurnDirection.RIGHT),
            FactPayload(distanceMeters = 800, direction = TurnDirection.STRAIGHT),
            FactPayload(direction = TurnDirection.ARRIVED),
            FactPayload(distanceMeters = 150, direction = TurnDirection.LEFT, gpsUncertain = true),
            FactPayload(distanceMeters = 90, direction = TurnDirection.RIGHT, recalculating = true),
            FactPayload(distanceMeters = 320, placeName = "Circle K", placeCategory = "fuel"),
        )
        directions.forEach { facts ->
            for (seed in 0L..12L) {
                val drive = ProbabilityDrive(chaos = 0.2f, rikardFrequency = 0.5f, seed = seed)
                val fresh = realEngine()
                val output = fresh.commentaryFor(facts, drive)
                assertSame(facts, output.facts)
                assertTrue(output.commentary.contains(realitySentence(facts)))
                assertTrue(creativeIsSafe(output.commentary.substringBefore("Verkligheten:"), facts))
                assertTrue(output.isGeneratedVariation)
                assertNotEquals(Provenance.ORIGINAL, output.sourceProvenance)
                assertEquals(instructionLabel(facts), instructionLabel(output.facts!!))
                if (facts.gpsUncertain) {
                    assertTrue(output.commentary.contains("Positionen är osäker."))
                    assertTrue(output.rikardInvolved)
                }
            }
        }
        val left = FactPayload(distanceMeters = 400, direction = TurnDirection.LEFT)
        assertEquals("400 m · Vänster", instructionLabel(left))
        assertEquals("Verkligheten: Sväng vänster om 400 meter.", realitySentence(left))
        assertFalse(leftImperativeIn(engine.commentaryFor(left, ProbabilityDrive(seed = 1)).commentary, "höger"))
    }

    @Test
    fun highChaosCanDenyThePreviousLineWithoutChangingTheManeuver() {
        val facts = FactPayload(distanceMeters = 400, direction = TurnDirection.LEFT)
        val engine = realEngine()
        val calm = ProbabilityDrive(chaos = 0f, rikardFrequency = 0f, seed = 3)
        val first = engine.commentaryFor(facts, calm)
        assertFalse(first.commentary.contains("Det har jag aldrig sagt."))
        val loud = ProbabilityDrive(chaos = 1f, rikardFrequency = 1f, seed = 4, mapMode = MapMode.IMPROBABILITY)
        val second = engine.commentaryFor(facts, loud)
        assertTrue(second.commentary.contains("Det har jag aldrig sagt."))
        assertTrue(second.commentary.contains("Sväng vänster om 400 meter."))
        assertFalse(second.commentary.contains("Sväng höger"))
        assertSame(facts, second.facts)
        assertEquals(400, second.glitch?.durationMillis)
        second.quotes.forEach { quote ->
            assertEquals(Provenance.ORIGINAL, quote.provenance)
            assertEquals(engine.archive().byId(quote.id)?.text, quote.text)
            assertFalse(second.commentary.contains(quote.text))
        }
    }

    @Test
    fun dontPanicShowsOriginalVerbatimAndLabelsGeneratedText() {
        val engine = realEngine()
        val before = engine.archive().originals().map { it.text }
        val output = engine.respond(
            se.pundaren.guide.engine.model.EngineInput(InputKind.DONT_PANIC),
            ProbabilityDrive(chaos = 0f, rikardFrequency = 0f, seed = 2),
        )
        assertEquals(42, output.transmissionNumber)
        assertTrue(output.isGeneratedVariation)
        assertTrue(output.commentary.contains("konstruerad"))
        assertEquals(1, output.quotes.size)
        val shown = output.quotes.single()
        assertEquals(Provenance.ORIGINAL, shown.provenance)
        assertTrue(shown.text in before)
        assertEquals(engine.archive().byId(shown.id)?.text, shown.text)
        assertFalse(output.commentary.contains(shown.text))
        assertEquals(before, engine.archive().originals().map { it.text })
        val again = engine.dontPanic(ProbabilityDrive(chaos = 0f, rikardFrequency = 0f, seed = 2))
        assertEquals(43, again.transmissionNumber)
    }

    @Test
    fun babelModesStaySatiricalAndCanReturnAStoredExampleVerbatim() {
        val engine = realEngine()
        val example = engine.archive().examples.first { it.id == "EX-ILLOGICAL-01" }
        val matched = engine.translate(example.original!!, BabelMode.KOSMISK.id, ProbabilityDrive(seed = 1))
        assertEquals(example.response, matched.commentary)
        assertEquals(example.id, matched.sourceId)
        assertEquals(Provenance.KONSTRUERAD, matched.sourceProvenance)
        assertTrue(matched.isGeneratedVariation)
        assertTrue(matched.quotes.isEmpty())
        assertEquals(0, matched.analysis?.answeredPercent)

        BabelMode.entries.forEach { mode ->
            val output = engine.translate(
                "Varför höll du inte vad du lovade?",
                mode.id,
                ProbabilityDrive(chaos = 0.3f, seed = 8),
            )
            assertEquals(mode.title, output.modeName)
            assertTrue(output.isGeneratedVariation)
            assertNotEquals(Provenance.ORIGINAL, output.sourceProvenance)
            assertFalse(output.commentary.contains("narciss"))
            output.quotes.forEach { quote ->
                assertEquals(engine.archive().byId(quote.id)?.text, quote.text)
                assertEquals(Provenance.ORIGINAL, quote.provenance)
                assertFalse(output.commentary.contains(quote.text))
            }
            val prose = output.analysis?.prose.orEmpty()
            assertTrue(prose.isNotBlank())
        }
    }

    @Test
    fun reverseAnalysisFlagsPatternsWithoutDiagnosing() {
        val engine = realEngine()
        val transcript = """
            Varför gjorde du inte det vi kom överens om?
            Varför säger du att jag inte bryr mig?
            Jag sa att jag skulle ringa.
            Jag sa aldrig att jag skulle ringa.
            Det är ditt fel att frågan fortfarande är obesvarad.
        """.trimIndent()
        val analysis = engine.analyze(transcript)
        assertFalse(analysis.satireSuppressed)
        assertTrue(analysis.unanswered.isNotEmpty())
        assertTrue(analysis.contradictions.isNotEmpty())
        assertTrue(analysis.patternNotes.isNotEmpty())
        assertTrue(analysis.summary.contains("diagnos"))
        assertTrue(analysis.disclaimer.contains("motiv"))
        val blob = (analysis.patternNotes + analysis.summary).joinToString(" ")
        assertFalse(blob.contains("narciss"))
        assertFalse(blob.contains("skör självbild"))
        assertFalse(blob.contains("motivet är"))
        assertFalse(blob.contains("psykopat"))

        val crisis = engine.analyze("Jag ska döda dig om du säger det igen.")
        assertTrue(crisis.satireSuppressed)
        assertTrue(crisis.supportReferral?.contains("112") == true)
        assertTrue(crisis.patternNotes.isEmpty())
        assertFalse(crisis.summary.contains("Rikard"))
    }

    @Test
    fun politeAndMarvinStillAgree() {
        val facts = FactPayload(distanceMeters = 50, direction = TurnDirection.STRAIGHT)
        listOf(AntiNavIntensity.POLITE, AntiNavIntensity.MARVIN, AntiNavIntensity.PASSIVE_AGGRESSIVE).forEach { intensity ->
            val output = realEngine().commentaryFor(
                facts,
                ProbabilityDrive(antiNavIntensity = intensity, seed = 6, mapMode = MapMode.POETIC),
            )
            assertTrue(output.commentary.contains("Fortsätt rakt fram i 50 meter."))
            assertFalse(output.commentary.contains("Sväng vänster"))
            assertFalse(output.commentary.contains("Sväng höger"))
        }
    }

    private fun loadReal() = ArchiveLoader.load(
        ArchiveBundle(
            quotesJson = data.resolve("quotes/quotes.json").readText(),
            rulesJson = data.resolve("babel-stoner/rules.json").readText(),
            examplesJson = data.resolve("babel-stoner/examples.json").readText(),
            lexiconJson = data.resolve("lexicon/lexicon.json").readText(),
            navigationJson = data.resolve("babel-stoner/navigation_comments.json").readText(),
        ),
    )

    private fun realEngine(): DefaultIllogicalEngine = DefaultIllogicalEngine().also { it.load(bundle()) }

    private fun bundle() = ArchiveBundle(
        quotesJson = data.resolve("quotes/quotes.json").readText(),
        rulesJson = data.resolve("babel-stoner/rules.json").readText(),
        examplesJson = data.resolve("babel-stoner/examples.json").readText(),
        lexiconJson = data.resolve("lexicon/lexicon.json").readText(),
        navigationJson = data.resolve("babel-stoner/navigation_comments.json").readText(),
    )

    private fun leftImperativeIn(commentary: String, word: String): Boolean =
        commentary.contains("Sväng $word") || commentary.contains("sväng $word")

    companion object {
        private const val TRUST_QUOTE =
            "Och så länge man själv inte står vid sitt ord kommer rädslan för att bli sviken alltid vara större än viljan att bygga tillit."
    }
}
