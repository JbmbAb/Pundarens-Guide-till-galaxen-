package se.pundaren.guide.engine.commentary

import se.pundaren.guide.engine.model.AntiNavIntensity
import se.pundaren.guide.engine.model.FactPayload
import se.pundaren.guide.engine.model.GlitchEffect
import se.pundaren.guide.engine.model.LoadedArchive
import se.pundaren.guide.engine.model.MapMode
import se.pundaren.guide.engine.model.NavComment
import se.pundaren.guide.engine.model.PoeticSensitivity
import se.pundaren.guide.engine.model.PresentedQuote
import se.pundaren.guide.engine.model.ProbabilityDrive
import se.pundaren.guide.engine.model.Provenance
import se.pundaren.guide.engine.model.TurnDirection
import se.pundaren.guide.engine.model.improbabilityAlias
import kotlin.random.Random

class SessionMemory {
    private val lines = ArrayDeque<String>()

    fun remember(line: String) {
        lines.addLast(line)
        while (lines.size > 8) lines.removeFirst()
    }

    fun isNotEmpty(): Boolean = lines.isNotEmpty()

    fun clear() {
        lines.clear()
    }
}

private val leftImperative = Regex("""(?i)sväng(?:er)?(?:\s+du)?\s+(?:åt\s+)?vänster|ta\s+till\s+vänster""")
private val rightImperative = Regex("""(?i)sväng(?:er)?(?:\s+du)?\s+(?:åt\s+)?höger|ta\s+till\s+höger""")
private val arrivedClaim = Regex("""(?i)du är framme|ankomsten är verklig""")
private val notArrived = Regex("""(?i)inte framme|inte är framme|inte anlänt""")
private val meters = Regex("""(\d+)\s*meter""")

fun realitySentence(facts: FactPayload): String {
    val core = when (val direction = facts.direction) {
        TurnDirection.LEFT -> maneuver("vänster", facts.distanceMeters)
        TurnDirection.RIGHT -> maneuver("höger", facts.distanceMeters)
        TurnDirection.STRAIGHT -> if (facts.distanceMeters != null) {
            "Fortsätt rakt fram i ${facts.distanceMeters} meter."
        } else {
            "Fortsätt rakt fram."
        }
        TurnDirection.ARRIVED -> "Du är framme."
        null -> placeSentence(facts)
    }
    val extras = buildList {
        facts.streetName?.takeIf { it.isNotBlank() }?.let { add("Gata: $it.") }
        if (facts.recalculating) add("Rutten räknas om.")
        if (facts.gpsUncertain) add("Positionen är osäker.")
    }
    return "Verkligheten: " + (listOf(core) + extras).joinToString(" ")
}

fun instructionLabel(facts: FactPayload): String {
    val distance = facts.distanceMeters?.let { "$it m" }
    val action = when (facts.direction) {
        TurnDirection.LEFT -> "Vänster"
        TurnDirection.RIGHT -> "Höger"
        TurnDirection.STRAIGHT -> "Rakt fram"
        TurnDirection.ARRIVED -> "Framme"
        null -> facts.placeName ?: "Ingen manöver"
    }
    return listOfNotNull(distance, action).joinToString(" · ")
}

fun creativeIsSafe(text: String, facts: FactPayload): Boolean {
    if (facts.direction != TurnDirection.LEFT && leftImperative.containsMatchIn(text)) return false
    if (facts.direction != TurnDirection.RIGHT && rightImperative.containsMatchIn(text)) return false
    if (facts.direction == TurnDirection.LEFT && rightImperative.containsMatchIn(text)) return false
    if (facts.direction == TurnDirection.RIGHT && leftImperative.containsMatchIn(text)) return false
    if (facts.direction != TurnDirection.ARRIVED && arrivedClaim.containsMatchIn(text)) return false
    if (facts.direction == TurnDirection.ARRIVED && notArrived.containsMatchIn(text)) return false
    val mentioned = meters.findAll(text).map { it.groupValues[1].toInt() }.toList()
    if (facts.distanceMeters == null && mentioned.isNotEmpty()) return false
    if (facts.distanceMeters != null && mentioned.any { it != facts.distanceMeters }) return false
    return true
}

private fun maneuver(word: String, distance: Int?): String =
    if (distance != null) "Sväng $word om $distance meter." else "Sväng $word."

private fun placeSentence(facts: FactPayload): String {
    val place = facts.placeName?.takeIf { it.isNotBlank() }
    val distance = facts.distanceMeters
    return when {
        place != null && distance != null -> "$place ligger $distance meter härifrån."
        place != null -> "$place."
        distance != null -> "Avståndet är $distance meter."
        else -> "Ingen ny manöver."
    }
}

class CommentaryGenerator {
    fun generate(
        facts: FactPayload,
        archive: LoadedArchive,
        drive: ProbabilityDrive,
        memory: SessionMemory,
        random: Random,
    ): CommentaryDraft {
        val comment = pickComment(facts, archive, drive, random)
        val parts = mutableListOf<String>()
        val deny = drive.chaos >= 0.99f && memory.isNotEmpty()
        if (deny) parts += "Det har jag aldrig sagt."
        paradoxLine(archive, drive, random)?.let { parts += it }
        if (comment != null) parts += comment.text
        if (facts.gpsUncertain) parts += "Rikard skyller ifrån sig."
        if (facts.recalculating) parts += "Du har kört fel. Rikard hävdar att vägen har flyttat sig."
        poeticLine(facts, drive)?.let { parts += it }
        var creative = parts.filter { it.isNotBlank() }.joinToString("\n\n")
        if (creative.isBlank() || !creativeIsSafe(creative, facts)) {
            creative = comment?.text?.takeIf { creativeIsSafe(it, facts) }
                ?: "Kommentaren tiger. Instruktionen gäller."
        }
        val reality = realitySentence(facts)
        val full = "$creative\n\n$reality"
        val quote = attachedQuote(archive, drive, random)
        if (quote != null && full.contains(quote.text)) {
            return draft(facts, creative, reality, comment, quote = null, deny = deny, drive = drive)
        }
        return draft(facts, creative, reality, comment, quote, deny, drive)
    }

    private fun draft(
        facts: FactPayload,
        creative: String,
        reality: String,
        comment: NavComment?,
        quote: PresentedQuote?,
        deny: Boolean,
        drive: ProbabilityDrive,
    ): CommentaryDraft {
        val rikard = (comment?.usesRikard == true) || facts.gpsUncertain || facts.recalculating || deny
        val glitch = if (rikard || drive.mapMode == MapMode.IMPROBABILITY) GlitchEffect(400) else null
        return CommentaryDraft(
            commentary = "$creative\n\n$reality",
            facts = facts,
            quote = quote,
            rikardInvolved = rikard,
            glitch = glitch,
            sourceId = comment?.id,
            sourceProvenance = comment?.provenance ?: Provenance.KONSTRUERAD,
        )
    }

    private fun pickComment(
        facts: FactPayload,
        archive: LoadedArchive,
        drive: ProbabilityDrive,
        random: Random,
    ): NavComment? {
        val eligible = archive.navigationComments.filter { comment ->
            comment.neverContradictsRoute &&
                situationMatches(comment, facts) &&
                creativeIsSafe(comment.text, facts)
        }
        if (eligible.isEmpty()) return null
        val preferRikard = random.nextFloat() < drive.rikardFrequency
        val preferred = when (drive.antiNavIntensity) {
            AntiNavIntensity.POLITE -> eligible.filter { !it.usesRikard }
            AntiNavIntensity.PASSIVE_AGGRESSIVE -> eligible
            AntiNavIntensity.MARVIN -> eligible.filter { it.usesRikard }.ifEmpty { eligible }
        }.let { pool ->
            if (preferRikard) pool.filter { it.usesRikard }.ifEmpty { pool } else pool
        }.ifEmpty { eligible }
        return preferred[random.nextInt(preferred.size)]
    }

    private fun situationMatches(comment: NavComment, facts: FactPayload): Boolean = when (comment.situation) {
        "turn" -> facts.direction == TurnDirection.LEFT || facts.direction == TurnDirection.RIGHT
        "straight" -> facts.direction == TurnDirection.STRAIGHT
        "arrival" -> facts.direction == TurnDirection.ARRIVED
        else -> true
    }

    private fun paradoxLine(archive: LoadedArchive, drive: ProbabilityDrive, random: Random): String? {
        if (drive.chaos < 0.45f) return null
        val pair = archive.valuePairs.ifEmpty { return null }[random.nextInt(archive.valuePairs.size)]
        val line = "Paradox, konstruerad: ${pair.a} och ${pair.b} byter plats. Det är inte ett citat och inte en manöver."
        return line
    }

    private fun poeticLine(facts: FactPayload, drive: ProbabilityDrive): String? {
        val line = when (drive.mapMode) {
            MapMode.PRACTICAL -> null
            MapMode.POETIC -> when (drive.poeticSensitivity) {
                PoeticSensitivity.LITERALIST -> null
                PoeticSensitivity.VOGON -> "En dikt ville skrivas här. Den fick inte röra pilen."
                PoeticSensitivity.EXISTENTIAL -> "Zonen kallas låg förväntan. Förväntan rör inte avståndet."
            }
            MapMode.IMPROBABILITY -> {
                val alias = improbabilityAlias(facts.placeCategory)
                "I osannolikhetsläget kallas stämningen för $alias. Namnet i instruktionen är oförändrat."
            }
        }
        return line?.takeIf { creativeIsSafe(it, facts) }
    }

    private fun attachedQuote(archive: LoadedArchive, drive: ProbabilityDrive, random: Random): PresentedQuote? {
        if (drive.chaos < 0.8f) return null
        val original = archive.originals().ifEmpty { return null }
        val quote = original[random.nextInt(original.size)]
        return PresentedQuote(
            id = quote.id,
            text = quote.text,
            provenance = Provenance.ORIGINAL,
            title = quote.title,
        )
    }
}

data class CommentaryDraft(
    val commentary: String,
    val facts: FactPayload,
    val quote: PresentedQuote?,
    val rikardInvolved: Boolean,
    val glitch: GlitchEffect?,
    val sourceId: String?,
    val sourceProvenance: Provenance,
)
