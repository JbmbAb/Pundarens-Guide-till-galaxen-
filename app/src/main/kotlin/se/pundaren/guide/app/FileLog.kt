package se.pundaren.guide.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Appens logg: logcat (taggen [TAG]), en fil i appens externa katalog som kan hämtas med
 * `adb pull /sdcard/Android/data/se.pundaren.guide/files/guide-logg.txt`, och de senaste raderna i
 * minnet för skärmen. Loggen innehåller sanningsdata (rutter, positioner under provtur) och vilka
 * rader Guiden sa; den lämnar aldrig enheten av sig själv.
 */
class FileLog(context: Context) {
    private val file = File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    val path: String get() = file.absolutePath

    @Synchronized
    fun line(text: String) {
        val row = "${LocalTime.now().format(clock)} $text"
        Log.i(TAG, row)
        runCatching { file.appendText(row + "\n") }
        _recent.update { (it + row).takeLast(KEEP) }
    }

    companion object {
        const val TAG = "Guide"
        const val FILE_NAME = "guide-logg.txt"
        private const val KEEP = 200
    }
}
