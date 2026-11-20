package se.pundaren.guide.babel

import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.ArchiveReader
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.api.OriginClass

/**
 * Babel-Stoner, Satirläget (spelet). Deterministiskt: samma inmatning, samma sessionsläge och
 * samma seed ger samma svar. Använder bara godkända regler (`DERIVED_RULE` med `APPROVED`: BS-01–BS-10 och,
 * sedan ägarbeslutet 2026-11-20 kl. 23.36, BS-11–BS-16), godkända arkivrader som regeln pekar på och
 * godkända satiriska repliker (EX-F-01–EX-F-07) (G8, RR-13).
 *
 * Byggaren skriver inga egna formuleringar. Repliken är en godkänd exempelpost, vald deterministiskt.
 * Analysläget och `:llm:api` ingår inte (etapp 7).
 */
class SatireMode(archive: ArchiveReader, private val config: Config = Config()) {

    data class Config(
        /** BS-08: efter så här många varv återvänder samtalet till början. Kandidatvärde, ofryst. */
        val returnToStartAfterTurns: Int = 3,
    )

    /** Regelbiblioteket som Satirläget får använda. */
    val rules: List<ArchiveEntry> = archive.all()
        .filter { it.kind == EntryKind.RULE && it.originClass == OriginClass.DERIVED_RULE && it.approval == Approval.APPROVED }
        .sortedBy { it.id.removePrefix("BS-").toIntOrNull() ?: Int.MAX_VALUE }

    private val quoteById: Map<String, ArchiveEntry> = archive.all()
        .filter {
            it.kind == EntryKind.QUOTE && it.approval == Approval.APPROVED &&
                it.originClass in setOf(OriginClass.ORIGINAL_VERBATIM, OriginClass.APPROVED_EDIT) &&
                "real_person_excerpt" !in it.tags
        }
        .associateBy { it.id }

    /** Godkända satiriska repliker (exempeldialoger). Inte EX-01-FORMULERING (BLOCKED) eller väntande poster. */
    val replies: List<ArchiveEntry> = archive.all()
        .filter { it.kind == EntryKind.EXAMPLE && it.approval == Approval.APPROVED && "archive_only" !in it.tags }
        .sortedBy { it.id }

    data class Session(val turns: List<String> = emptyList())

    data class SatireReply(
        val label: String,
        val ruleId: String,
        val ruleFunction: String,
        val ruleBasis: String?,
        /** Den inmatning regeln tillämpas på. Vid BS-08 är det samtalets första replik. */
        val appliedTo: String,
        /** En godkänd arkivrad som regeln pekar på, eller null. Märkt PRIVATE (04, 1.4); styr inte uppspelning. */
        val exampleLineId: String?,
        val exampleText: String?,
        /** Godkänd satirisk replik ur arkivet, eller null. */
        val replyLineId: String?,
        val replyText: String?,
        val session: Session,
    ) {
        fun render(): String = buildString {
            append("$label · ").append(ruleId).append(": ").append(ruleFunction)
            ruleBasis?.let { append(" (grund: ").append(it).append(')') }
            append("\nTillämpas på: «").append(appliedTo).append('»')
            if (exampleText != null) append("\nUr arkivet (").append(exampleLineId).append("): ").append(exampleText)
            if (replyText != null) append("\nReplik (").append(replyLineId).append("): ").append(replyText)
        }
    }

    /**
     * @param inVehicle om enheten är i fordon. Då används inte repliker märkta "bara till fots" (F-11, 23.36).
     *   Standard är det försiktiga valet `true`, eftersom Babel inte får läsa sanningskärnan.
     */
    fun reply(input: String, session: Session, seed: Long, inVehicle: Boolean = true): SatireReply {
        require(rules.isNotEmpty()) { "Inga godkända BS-regler i arkivet" }
        val text = input.trim()
        val next = Session(session.turns + text)
        val rule = if (next.turns.size > config.returnToStartAfterTurns) {
            rules.firstOrNull { it.id == "BS-08" } ?: pick(text, seed, next.turns.size)
        } else pick(text, seed, next.turns.size)
        val appliedTo = if (rule.id == "BS-08") next.turns.first() else text
        val example = rule.tags.filter { it.startsWith("quote:") }.map { it.removePrefix("quote:") }
            .mapNotNull { quoteById[it] }.sortedBy { it.id }.firstOrNull()
        val usableReplies = replies.filter { !(inVehicle && "foot_only" in it.tags) }
        val reply = if (usableReplies.isEmpty()) null else usableReplies[(((seed * 7 + next.turns.size + text.length) % usableReplies.size + usableReplies.size) % usableReplies.size).toInt()]
        return SatireReply(
            label = "Satir",
            ruleId = rule.id,
            ruleFunction = rule.text,
            ruleBasis = rule.tags.firstOrNull { it.startsWith("basis:") }?.removePrefix("basis:")?.ifBlank { null },
            appliedTo = appliedTo,
            exampleLineId = example?.id,
            exampleText = example?.text,
            replyLineId = reply?.id,
            replyText = reply?.text,
            session = next,
        )
    }

    private fun pick(text: String, seed: Long, turn: Int): ArchiveEntry {
        var h = seed * 31 + turn
        for (c in text.lowercase()) h = h * 1_000_003L + c.code
        val idx = ((h % rules.size) + rules.size) % rules.size
        return rules[idx.toInt()]
    }
}
