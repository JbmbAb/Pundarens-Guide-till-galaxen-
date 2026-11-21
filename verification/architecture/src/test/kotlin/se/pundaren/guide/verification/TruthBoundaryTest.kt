package se.pundaren.guide.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.truth.api.InstructionVoice
import se.pundaren.guide.truth.api.TruthDisplayReader
import se.pundaren.guide.truth.core.TruthInput
import se.pundaren.guide.truth.core.kernel.TruthKernel
import java.io.File
import java.lang.reflect.Modifier

/**
 * Sanningskärnans gräns sedan etapp 4 (03, G-2 och G-5), nu när logiken ligger i `:truth:core`
 * (ren Kotlin) och Android-adaptrarna i `:truth:device`.
 */
class TruthBoundaryTest {
    private val root = File(System.getProperty("guide.repoRoot"))

    private val tramsPackages = listOf(
        "se.pundaren.guide.engine", "se.pundaren.guide.archive", "se.pundaren.guide.safetygate",
        "se.pundaren.guide.conductor", "se.pundaren.guide.babel", "se.pundaren.guide.voice", "se.pundaren.guide.app",
    )

    private fun kotlinFiles(dir: String): List<File> =
        root.resolve(dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun `G-2 all inmatning i kärnan är märkt TruthInput`() {
        val readOnly = setOf(
            "getEvents", "getNavigation", "getPosition", "getSafety", "getGuidance", "getMap",
            "equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait",
        )
        val inputs = TruthKernel::class.java.methods.filter {
            Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !it.isSynthetic && it.name !in readOnly
        }
        assertTrue("kärnan har inmatningsmetoder", inputs.size >= 10)
        inputs.forEach { m ->
            assertTrue("${m.name} saknar @TruthInput", m.isAnnotationPresent(TruthInput::class.java))
            assertEquals("${m.name} returnerar något", Void.TYPE, m.returnType)
        }
    }

    @Test fun `G-2 bara truth-device och testkod slår på TruthInput`() {
        val allowed = listOf("truth/core/", "truth/device/", "verification/architecture/src/test/")
        val hits = root.walkTopDown()
            .onEnter { it.name !in setOf(".git", "build", ".gradle", "docs", "data", ".kotlin", "lokalt", "prototyp") }
            .filter { it.isFile && it.extension in setOf("kt", "kts") }
            .filter { it.readText().contains("TruthInput") }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filterNot { path -> allowed.any { path.startsWith(it) } }
            .toList()
        assertEquals("TruthInput används utanför sanningslagret", emptyList<String>(), hits)
    }

    @Test fun `G-5 kärnans inmatning tar inga tramstyper och text bara som målets namn`() {
        val withText = mutableListOf<String>()
        TruthKernel::class.java.methods.filter { it.isAnnotationPresent(TruthInput::class.java) }.forEach { m ->
            m.parameterTypes.forEach { p ->
                assertTrue("${m.name} tar emot ${p.name}", tramsPackages.none { p.name.startsWith(it) })
                assertTrue("${m.name} tar emot en callback", !p.name.startsWith("kotlin.jvm.functions") && !p.name.startsWith("java.util.function"))
                if (p == String::class.java || p == CharSequence::class.java) withText += m.name
            }
        }
        // Den enda texten som går in i sanningen är målets namn, och den kommer från platsindexet
        // eller från användarens eget val – aldrig från Illogical Engine. Avvikelse E4-D4.
        assertEquals(listOf("routeStarted"), withText)
    }

    @Test fun `G-1 sanningslagret importerar inget ur tramslagret`() {
        for (dir in listOf("truth/api/src/main", "truth/core/src/main", "truth/device/src/main")) {
            val files = kotlinFiles(dir)
            assertTrue("$dir hittas", files.isNotEmpty())
            val hits = files.flatMap { f ->
                f.readLines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }
                    .filter { imp -> tramsPackages.any { imp.startsWith(it) } }.map { "${f.name}: $it" }
            }
            assertEquals("tramsimporter i $dir", emptyList<String>(), hits)
        }
    }

    @Test fun `rösten känner varken motorn, SafetyGate eller sanningskärnan`() {
        val forbidden = listOf(
            "se.pundaren.guide.engine", "se.pundaren.guide.archive", "se.pundaren.guide.safetygate", "se.pundaren.guide.conductor",
            "se.pundaren.guide.babel", "se.pundaren.guide.truth.core", "se.pundaren.guide.truth.device", "se.pundaren.guide.app",
        )
        val files = kotlinFiles("voice/src/main")
        assertTrue(files.isNotEmpty())
        val hits = files.flatMap { f -> f.readLines().filter { l -> l.startsWith("import ") && forbidden.any { l.contains(it) } }.map { "${f.name}: $it" } }
        assertEquals(emptyList<String>(), hits)
    }

    @Test fun `utgående porten för besked kan inte läsa eller ändra sanningsdata`() {
        // InstructionVoice: sanningen talar, rösten svarar bara med om beskedet spelas.
        val methods = InstructionVoice::class.java.methods.associateBy { it.name }
        assertEquals(setOf("speakInstruction", "stopInstruction", "getInstructionSpeaking"), methods.keys)
        assertEquals(listOf(String::class.java), methods.getValue("speakInstruction").parameterTypes.toList())
        assertEquals(Void.TYPE, methods.getValue("speakInstruction").returnType)
        TruthDisplayReader::class.java.methods.forEach { m ->
            assertEquals("${m.name} tar parametrar", 0, m.parameterCount)
            assertTrue("${m.name} returnerar muterbart flöde", !m.returnType.name.contains("Mutable"))
        }
    }

    @Test fun `appen saknar INTERNET-behörighet och ingen modul lägger till den`() {
        val manifests = root.walkTopDown()
            .onEnter { it.name !in setOf(".git", "build", ".gradle", "docs", "data", ".kotlin", "lokalt", "prototyp") }
            .filter { it.name == "AndroidManifest.xml" }.toList()
        assertTrue("manifesten hittas", manifests.size >= 3)
        val adds = Regex("""<uses-permission\s+android:name="android\.permission\.INTERNET"\s*/>""")
        assertEquals(emptyList<String>(), manifests.filter { adds.containsMatchIn(it.readText()) }.map { it.relativeTo(root).invariantSeparatorsPath })
        val app = root.resolve("app/src/main/AndroidManifest.xml").readText()
        assertTrue(Regex("""android\.permission\.INTERNET"\s+tools:node="remove"""").containsMatchIn(app))
    }

    @Test fun `kartstilarna har samma lager, inga nätadresser och ingen sanningsfärg`() {
        val dir = root.resolve("app/src/main/assets/stil")
        val styles = listOf("praktisk", "poetisk", "osannolik").map { dir.resolve("$it.json").readText() }
        assertEquals(3, styles.size)
        val ids = styles.map { s -> Regex(""""id":\s*"([^"]+)"""").findAll(s).map { it.groupValues[1] }.toList() }
        assertTrue("stilarna har lager", ids[0].size >= 10)
        assertEquals("Poetisk har samma lager som Praktisk", ids[0], ids[1])
        assertEquals("Osannolik har samma lager som Praktisk", ids[0], ids[2])
        styles.forEach { s ->
            assertTrue("nätadress i stilen", !s.contains("http://") && !s.contains("https://"))
            assertTrue("glyfer eller sprite i stilen", !s.contains("\"glyphs\"") && !s.contains("\"sprite\""))
            assertTrue("turkost i kartan (F-02)", !s.lowercase().contains("4af8e3"))
        }
    }
}
