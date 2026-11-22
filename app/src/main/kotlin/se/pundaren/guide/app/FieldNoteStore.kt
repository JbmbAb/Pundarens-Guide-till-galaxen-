package se.pundaren.guide.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.pundaren.guide.encyclopedia.FieldNote
import se.pundaren.guide.encyclopedia.FieldNotebook
import se.pundaren.guide.truth.api.GeoPoint
import java.io.File
import java.time.Instant

/**
 * Fältanteckningarna på disk: en rad per anteckning i appens interna lagring, bara tillägg.
 * Filen går inte att läsa utifrån, säkerhetskopieras inte och lämnar aldrig enheten. Arkivdatabasen
 * i 04 finns inte än; filen är dess ersättare tills vidare (avvikelse E7-D2).
 */
class FieldNoteStore(private val file: File, private val log: (String) -> Unit) {
    private val book = FieldNotebook(
        runCatching { if (file.isFile) file.readLines().mapNotNull(FieldNotebook::decode) else emptyList() }
            .onFailure { log("ANTECKNINGAR kunde inte läsas: $it") }.getOrDefault(emptyList()),
    )

    private val _notes = MutableStateFlow(book.notes)
    val notes: StateFlow<List<FieldNote>> = _notes.asStateFlow()

    /** Sparar anteckningen ordagrant. Falskt om texten var tom eller filen inte gick att skriva. */
    @Synchronized
    fun add(text: String, point: GeoPoint?, placeName: String?): Boolean {
        val note = book.add(text, Instant.now(), point, placeName) ?: return false
        _notes.value = book.notes
        return runCatching {
            file.parentFile?.mkdirs()
            file.appendText(FieldNotebook.encode(note) + "\n")
            true
        }.onFailure { log("ANTECKNINGAR kunde inte sparas: $it") }.getOrDefault(false)
    }
}
