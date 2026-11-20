package se.pundaren.guide.archive.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Kör SQL-koden i 04 (avsnitt 1.5 och 1.6) mot vanlig SQLite via JDBC. Bevisar triggerna i SQLite,
 * **inte** i Room eller SQLDelight och inte på Android (A-13 förblir delvis NOT_VERIFIED).
 */
class SqliteTriggerTest {
    private lateinit var db: Connection
    private val schema = javaClass.getResource("/archive/schema-v1.sql")!!.readText()
    private val entries = ArchiveImporter.import(ArchiveSources.fromRepo(File(System.getProperty("guide.repoRoot"))))

    @Before fun open() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use { it.executeUpdate("PRAGMA foreign_keys = ON") }
        execScript(schema)
        val ps = db.prepareStatement(
            "INSERT INTO archive_entry (id, origin_class, subtype, kind, text, text_sha256, verification, approval, visibility, author, source_ref, derived_from, imported_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
        )
        for (e in entries.sortedBy { if (it.derivedFrom == null) 0 else 1 }) {
            ps.setString(1, e.id); ps.setString(2, e.originClass.name); ps.setString(3, e.subtype)
            ps.setString(4, e.kind.name.lowercase()); ps.setString(5, e.text); ps.setString(6, e.textSha256)
            ps.setString(7, e.verification.name); ps.setString(8, e.approval.name); ps.setString(9, e.visibility.name)
            ps.setString(10, e.author); ps.setString(11, e.sourceRef)
            ps.setString(12, e.derivedFrom?.takeIf { d -> entries.any { it.id == d } })
            ps.setString(13, "2026-11-20T00:00:00Z")
            ps.executeUpdate()
        }
    }

    @After fun close() = db.close()

    private fun execScript(sql: String) {
        // Dela på satser; triggerkroppar innehåller semikolon och avslutas med END;
        val stmts = mutableListOf<String>()
        val cur = StringBuilder()
        var inTrigger = false
        for (line in sql.lines()) {
            val l = line.substringBefore("--").trimEnd()
            if (l.isBlank()) continue
            cur.appendLine(l)
            if (l.trimStart().uppercase().startsWith("CREATE TRIGGER")) inTrigger = true
            val done = if (inTrigger) l.trim().uppercase().endsWith("END;") else l.trim().endsWith(";")
            if (done) { stmts += cur.toString(); cur.clear(); inTrigger = false }
        }
        db.createStatement().use { st -> stmts.forEach { st.execute(it) } }
    }

    private fun expectAbort(sql: String, contains: String) {
        try {
            db.createStatement().use { it.executeUpdate(sql) }
            fail("Förväntade ABORT: $sql")
        } catch (e: SQLException) {
            assertTrue("fel meddelande: ${e.message}", e.message!!.contains(contains))
        }
    }

    private fun count(sql: String): Int = db.createStatement().use { st -> st.executeQuery(sql).use { it.next(); it.getInt(1) } }

    @Test fun `update av originaltext stoppas`() =
        expectAbort("UPDATE archive_entry SET text = 'x' WHERE id = 'P-001'", "ORIGINAL_VERBATIM är skrivskyddad")

    @Test fun `delete av original stoppas`() =
        expectAbort("DELETE FROM archive_entry WHERE id = 'C-007'", "ORIGINAL_VERBATIM kan inte raderas")

    @Test fun `uppgradering till original stoppas`() =
        expectAbort("UPDATE archive_entry SET origin_class = 'ORIGINAL_VERBATIM' WHERE id = 'V-001'", "Endast import från källa")

    @Test fun `revisionshistoriken är bara tillägg`() {
        db.createStatement().use { it.executeUpdate("UPDATE archive_entry SET visibility = 'COMPANY' WHERE id = 'C-001'") }
        assertEquals(1, count("SELECT COUNT(*) FROM archive_revision WHERE entry_id = 'C-001' AND field = 'visibility'"))
        assertEquals(2, count("SELECT revision FROM archive_entry WHERE id = 'C-001'"))
        expectAbort("UPDATE archive_revision SET reason = 'x'", "bara tillägg")
        expectAbort("DELETE FROM archive_revision", "bara tillägg")
    }

    @Test fun `syntetisk post får ändras`() {
        db.createStatement().use { it.executeUpdate("UPDATE archive_entry SET text = 'ny' WHERE id = 'V-001'") }
        assertEquals(1, count("SELECT COUNT(*) FROM archive_entry WHERE id = 'V-001' AND text = 'ny'"))
    }

    /**
     * Migreringsrisken i 04: en migrering som bygger om tabellen (så som Room ofta gör vid schemaändring)
     * tappar triggerna tyst. Testet visar att det händer, att kontrollen `triggersPresent` upptäcker det och
     * att skyddet är tillbaka när triggerna skapas igen i migreringen.
     */
    @Test fun `ombyggnad av tabellen tappar triggerna och upptäcks`() {
        val expected = setOf("original_immutable", "original_undeletable", "no_promotion_to_original", "log_visibility_change")
        fun present(): Set<String> = db.createStatement().use { st ->
            st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'trigger' AND tbl_name = 'archive_entry'").use { rs ->
                buildSet { while (rs.next()) add(rs.getString(1)) }
            }
        }
        assertEquals(expected, present())
        db.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = OFF")
            st.execute("CREATE TABLE archive_entry_new AS SELECT * FROM archive_entry")
            st.execute("DROP TABLE archive_entry")
            st.execute("ALTER TABLE archive_entry_new RENAME TO archive_entry")
        }
        assertTrue("triggerna borde ha försvunnit", present().isEmpty())
        // Utan triggers går originalet att ändra – det är risken.
        db.createStatement().use { it.executeUpdate("UPDATE archive_entry SET text = text WHERE id = 'P-002'") }
        // Migreringen återskapar triggerna ur samma schema.
        val entryTriggers = Regex("CREATE TRIGGER (\\w+)[\\s\\S]*?\\bEND;").findAll(schema)
            .filter { !it.groupValues[1].startsWith("revision_") }.map { it.value }.toList()
        assertEquals(4, entryTriggers.size)
        db.createStatement().use { st -> entryTriggers.forEach { st.execute(it) } }
        assertEquals(expected, present())
        expectAbort("UPDATE archive_entry SET text = 'x' WHERE id = 'P-002'", "ORIGINAL_VERBATIM är skrivskyddad")
    }
}
