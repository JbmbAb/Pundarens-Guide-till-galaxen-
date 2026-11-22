package se.pundaren.guide.truth.device

import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.core.spar.TrackPoint
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Det egna spåret på disk, så att vägen tillbaka överlever att appen stängs eller kraschar.
 *
 * En rad per sparad punkt (`epokmillisekunder;lat;lon;noggrannhet`), bara tillägg. Filen ligger i
 * appens interna lagring: den går inte att läsa utifrån, följer inte med i någon säkerhetskopia
 * (`data_extraction_rules.xml`) och lämnar aldrig enheten. Det är verkliga positioner, alltså
 * personuppgifter.
 */
internal class TrackStore(private val directory: File, private val log: (String) -> Unit) {
    private val current = File(directory, CURRENT)

    /** Läser det pågående spåret. Rader som inte går att tolka hoppas över. */
    fun load(): List<TrackPoint> {
        if (!current.isFile) return emptyList()
        return runCatching {
            current.useLines { lines ->
                lines.mapNotNull { line ->
                    val p = line.split(';')
                    if (p.size != 4) return@mapNotNull null
                    val at = p[0].toLongOrNull() ?: return@mapNotNull null
                    val lat = p[1].toDoubleOrNull() ?: return@mapNotNull null
                    val lon = p[2].toDoubleOrNull() ?: return@mapNotNull null
                    val accuracy = p[3].toDoubleOrNull() ?: return@mapNotNull null
                    TrackPoint(GeoPoint(lat, lon), accuracy, Instant.ofEpochMilli(at))
                }.toList()
            }
        }.onFailure { log("SPÅR kunde inte läsas: $it") }.getOrDefault(emptyList())
    }

    fun append(point: TrackPoint) {
        runCatching {
            directory.mkdirs()
            current.appendText(row(point))
        }.onFailure { log("SPÅR kunde inte sparas: $it") }
    }

    /** Skriver om filen med just de här punkterna, när spåret har glesats ut. */
    fun rewrite(points: List<TrackPoint>) {
        runCatching {
            directory.mkdirs()
            val temporary = File(directory, "$CURRENT.tmp")
            temporary.writeText(points.joinToString("") { row(it) })
            if (!temporary.renameTo(current)) { current.delete(); temporary.renameTo(current) }
        }.onFailure { log("SPÅR kunde inte skrivas om: $it") }
    }

    /**
     * Lägger undan det pågående spåret under sitt startklockslag och börjar på ett nytt.
     * De [KEEP] senaste undanlagda spåren sparas, äldre tas bort.
     */
    fun archive(startedUtc: Instant?) {
        if (!current.isFile) return
        runCatching {
            val name = "spar-" + STAMP.format((startedUtc ?: Instant.now()).atZone(ZoneId.systemDefault())) + ".csv"
            var target = File(directory, name)
            var n = 1
            while (target.exists()) target = File(directory, name.removeSuffix(".csv") + "-${n++}.csv")
            if (!current.renameTo(target)) current.delete()
            directory.listFiles { f -> f.name.startsWith("spar-") && f.name.endsWith(".csv") }
                ?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        }.onFailure { log("SPÅR kunde inte läggas undan: $it") }
    }

    private fun row(p: TrackPoint) =
        String.format(Locale.ROOT, "%d;%.6f;%.6f;%.1f\n", p.atUtc.toEpochMilli(), p.point.lat, p.point.lon, p.accuracyMeters)

    private companion object {
        const val CURRENT = "aktuellt.csv"
        const val KEEP = 30
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
    }
}
