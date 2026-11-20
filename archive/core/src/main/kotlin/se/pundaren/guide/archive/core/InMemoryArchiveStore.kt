package se.pundaren.guide.archive.core

import se.pundaren.guide.archive.api.ArchiveChange
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.ArchiveStore
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.api.RevisionRecord
import se.pundaren.guide.archive.api.WriteResult

/**
 * Referensimplementation av [ArchiveStore] på modellnivå, med samma regler som triggerna i 04, 1.6.
 * Den riktiga lagringen (Room eller SQLDelight, A-13) väljs senare och ska klara samma kontraktstester.
 */
class InMemoryArchiveStore : ArchiveStore {
    private val entries = LinkedHashMap<String, ArchiveEntry>()
    private val log = mutableListOf<RevisionRecord>()

    override fun all(): List<ArchiveEntry> = entries.values.toList()
    override fun get(id: String): ArchiveEntry? = entries[id]
    override fun revisions(): List<RevisionRecord> = log.toList()

    override fun importFromSource(entry: ArchiveEntry): WriteResult {
        if (entries.containsKey(entry.id)) return WriteResult.Rejected("Id ${entry.id} finns redan; nytt original får nytt id-suffix")
        if (entry.textSha256 != sha256Hex(entry.text)) return WriteResult.Rejected("Kontrollsumman stämmer inte för ${entry.id}")
        entries[entry.id] = entry
        return WriteResult.Ok
    }

    override fun change(id: String, change: ArchiveChange): WriteResult {
        val old = entries[id] ?: return WriteResult.Rejected("Okänt id $id")
        val isOriginal = old.originClass == OriginClass.ORIGINAL_VERBATIM
        return when (change) {
            is ArchiveChange.SetText ->
                if (isOriginal) WriteResult.Rejected("ORIGINAL_VERBATIM är skrivskyddad")
                else commit(old, old.copy(text = change.text, textSha256 = sha256Hex(change.text)), "text", old.text, change.text, change.changedBy, change.reason)
            is ArchiveChange.SetOriginClass -> when {
                change.originClass == OriginClass.ORIGINAL_VERBATIM && !isOriginal ->
                    WriteResult.Rejected("Endast import från källa kan skapa ORIGINAL_VERBATIM")
                isOriginal && change.originClass != OriginClass.ORIGINAL_VERBATIM ->
                    WriteResult.Rejected("ORIGINAL_VERBATIM är skrivskyddad")
                else -> commit(old, old.copy(originClass = change.originClass), "origin_class", old.originClass.name, change.originClass.name, change.changedBy, "klassbyte")
            }
            is ArchiveChange.SetVisibility ->
                commit(old, old.copy(visibility = change.visibility), "visibility", old.visibility.name, change.visibility.name, change.changedBy, "användarval")
            is ArchiveChange.SetApproval ->
                commit(old, old.copy(approval = change.approval), "approval", old.approval.name, change.approval.name, change.changedBy, "godkännande")
        }
    }

    override fun delete(id: String): WriteResult {
        val old = entries[id] ?: return WriteResult.Rejected("Okänt id $id")
        if (old.originClass == OriginClass.ORIGINAL_VERBATIM) return WriteResult.Rejected("ORIGINAL_VERBATIM kan inte raderas")
        entries.remove(id)
        return WriteResult.Ok
    }

    private fun commit(old: ArchiveEntry, new: ArchiveEntry, field: String, oldV: String?, newV: String?, by: String, reason: String): WriteResult {
        val next = new.copy(revision = old.revision + 1)
        entries[old.id] = next
        log += RevisionRecord(old.id, next.revision, by, field, oldV, newV, reason)
        return WriteResult.Ok
    }

    companion object {
        fun of(entries: List<ArchiveEntry>): InMemoryArchiveStore = InMemoryArchiveStore().also { s ->
            entries.forEach { e -> check(s.importFromSource(e) == WriteResult.Ok) { "Import misslyckades för ${e.id}" } }
        }
    }
}
