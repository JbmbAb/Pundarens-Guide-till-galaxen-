package se.pundaren.guide.engine

import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.api.Mechanism
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.api.Verification
import se.pundaren.guide.archive.api.Visibility
import se.pundaren.guide.archive.core.ArchiveImporter
import se.pundaren.guide.archive.core.ArchiveSources
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import se.pundaren.guide.archive.core.sha256Hex
import se.pundaren.guide.truth.api.*
import java.io.File
import java.time.Instant

object Fixtures {
    val repoRoot = File(System.getProperty("guide.repoRoot"))
    val realArchive by lazy { InMemoryArchiveStore.of(ArchiveImporter.import(ArchiveSources.fromRepo(repoRoot))) }

    /** Testrader. Finns bara i testkoden, levereras aldrig och är inte förslag till appen. */
    fun line(
        id: String, text: String, approval: Approval = Approval.APPROVED,
        origin: OriginClass = OriginClass.ORIGINAL_VERBATIM, tags: Set<String> = emptySet(),
        kind: EntryKind = EntryKind.QUOTE,
    ) = ArchiveEntry(
        id = id, originClass = origin, subtype = "testfixtur", kind = kind, text = text, textSha256 = sha256Hex(text),
        language = "sv", verification = Verification.UNKNOWN, approval = approval, visibility = Visibility.PRIVATE,
        author = "testfixtur", sourceRef = null, sourceNote = null, derivedFrom = null, legacyId = null,
        category = "test", mechanisms = setOf(Mechanism.M1), tags = tags,
    )

    private val t0: Instant = Instant.parse("2026-11-20T21:00:00Z")
    fun at(ms: Long): Instant = t0.plusMillis(ms)

    fun routeStarted(id: String, ms: Long = 0, mode: TravelMode = TravelMode.PASSENGER) =
        RouteStarted(id, ms, at(ms), "EXP-1", "Orsa", 12_345, 900, mode)
    fun maneuverCompleted(id: String, ms: Long) = ManeuverCompleted(id, ms, at(ms), "EXP-1", "M-$id")
    fun offRoute(id: String, ms: Long) = OffRoute(id, ms, at(ms), "EXP-1", 40, 5)
    fun emergency(id: String, ms: Long) = EmergencyActivated(id, ms, at(ms), "EXP-1", EmergencySource.USER)

    /** Händelsesimulator: en deterministisk sekvens av alla fjorton typer, inklusive kantfallen i 05 §6. */
    fun simulate(n: Int, seed: Long): List<TruthEvent> {
        val rnd = java.util.SplittableRandom(seed)
        var ms = 0L
        return (0 until n).map { i ->
            ms += 500 + rnd.nextInt(4_000)
            val id = "EV-%04d".format(i)
            when (rnd.nextInt(14)) {
                0 -> RouteStarted(id, ms, at(ms), "EXP-1", "Orsa", rnd.nextInt(1, 50_000), rnd.nextInt(60, 5_000), TravelMode.PASSENGER)
                1 -> ManeuverUpcoming(id, ms, at(ms), "EXP-1", "M$i", "turn", if (rnd.nextBoolean()) "left" else "right", rnd.nextInt(10, 800), if (i % 7 == 0) 2.0 else rnd.nextDouble(1.0, 90.0), "Kyrkogatan")
                2 -> ManeuverCompleted(id, ms, at(ms), "EXP-1", "M$i")
                3 -> OffRoute(id, ms, at(ms), "EXP-1", rnd.nextInt(20, 300), rnd.nextInt(1, 30))
                4 -> Rerouted(id, ms, at(ms), "EXP-1", rnd.nextInt(100, 20_000), rnd.nextInt(60, 3_000), "avvikelse")
                5 -> Stationary(id, ms, at(ms), "EXP-1", rnd.nextInt(10, 600))
                6 -> Arrived(id, ms, at(ms), "EXP-1", "Orsa", rnd.nextInt(60, 9_000), rnd.nextInt(100, 90_000))
                7 -> PositionQualityChanged(id, ms, at(ms), "EXP-1", rnd.nextDouble(3.0, 200.0), rnd.nextDouble(0.0, 60.0), PositionQuality.entries[rnd.nextInt(3)])
                8 -> BatteryLow(id, ms, at(ms), "EXP-1", rnd.nextInt(1, 20), null)
                9 -> ConvoyGap(id, ms, at(ms), "EXP-1", "Peer", rnd.nextInt(10, 5_000), rnd.nextInt(0, 120))
                10 -> ConvoyContactLost(id, ms, at(ms), "EXP-1", "Peer", rnd.nextInt(30, 600))
                11 -> EmergencyActivated(id, ms, at(ms), "EXP-1", EmergencySource.USER)
                12 -> EmergencyCleared(id, ms, at(ms), "EXP-1")
                else -> TimeOfDayChanged(id, ms, at(ms), "EXP-1", DayPhase.entries[rnd.nextInt(3)])
            }
        }
    }
}
