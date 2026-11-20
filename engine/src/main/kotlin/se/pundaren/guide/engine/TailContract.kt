package se.pundaren.guide.engine

import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.ArchiveReader
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.engine.api.PresentedTail
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.api.TruthEvent

/** Automatisk kontroll av svarskontraktet i 05, avsnitt 5. Returnerar brotten; tom lista betyder godkänt. */
object TailContract {
    fun violations(
        candidate: TailCandidate,
        event: TruthEvent,
        archive: ArchiveReader,
        travelMode: TravelMode,
        presented: List<PresentedTail>,
        maxLifetimeMillis: Long = 10_000,
    ): List<String> = buildList {
        if (candidate.triggeredBy != event.eventId) add("1: triggeredBy pekar inte på händelsen")
        if (candidate.validUntilMonotonicMillis - event.monotonicMillis > maxLifetimeMillis) add("2: livslängd över ${maxLifetimeMillis} ms")
        if (travelMode != TravelMode.FOOT && VoiceGuards.hasDirectionWord(candidate.text)) add("3: riktningsord i fordonsläge")
        if (travelMode != TravelMode.FOOT && candidate.footOnly) add("3: rad som bara får användas till fots i fordonsläge")
        if (!candidate.footOnly && (VoiceGuards.hasDirectionWord(candidate.text) || VoiceGuards.soundsLikeInstruction(candidate.text))) add("3: riktningsord eller beskedsliknande rad saknar footOnly")
        val src = archive.get(candidate.lineId)
        if (src == null) add("4: lineId saknas i arkivet")
        else {
            if (src.approval != Approval.APPROVED) add("4: källposten är inte APPROVED")
            if (src.originClass != candidate.origin) add("4: ursprungsklassen stämmer inte")
            if (src.text != candidate.text) add("4: texten skiljer sig från arkivposten")
            if (candidate.visibility.ordinal > src.visibility.ordinal) add("5: synligheten är mindre strikt än källan")
        }
        if (candidate.contradicts != null && presented.none { it.tailId == candidate.contradicts }) {
            add("M2: förnekar något som inte presenterats")
        }
    }
}
