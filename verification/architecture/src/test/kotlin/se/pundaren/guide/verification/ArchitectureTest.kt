package se.pundaren.guide.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.engine.api.IllogicalEngine
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.safetygate.GateInput
import se.pundaren.guide.safetygate.PresentationDecision
import se.pundaren.guide.safetygate.SafetyGate
import se.pundaren.guide.truth.api.TruthEvent
import se.pundaren.guide.truth.api.TruthEventStream
import se.pundaren.guide.truth.api.TruthStateReader
import java.io.File
import java.lang.reflect.Modifier

/** Arkitekturtester för dioden (03, avsnitt 3) och SG-23 (06). */
class ArchitectureTest {
    private val root = File(System.getProperty("guide.repoRoot"))

    private fun sources(module: String): List<File> =
        root.resolve("$module/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    private fun imports(module: String): Map<String, List<String>> = sources(module).associate { f ->
        f.relativeTo(root).path to f.readLines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }
    }

    private fun assertNoImports(module: String, forbidden: List<String>) {
        val hits = imports(module).flatMap { (file, imps) -> imps.filter { i -> forbidden.any { i.startsWith(it) } }.map { "$file: $it" } }
        assertEquals("Förbjudna importer i $module", emptyList<String>(), hits)
    }

    private val network = listOf("java.net", "javax.net", "okhttp3", "io.ktor", "java.nio.channels", "android.net")
    private val android = listOf("android.", "androidx.", "com.google", "com.android")

    @Test fun `G-1 motorn, babel och safetygate når inte sanningskärnan eller konvojen`() {
        val truthCore = listOf(
            "se.pundaren.guide.truth.core", "se.pundaren.guide.truth.device", "se.pundaren.guide.convoy", "se.pundaren.guide.voice",
        )
        listOf("engine", "babel", "safetygate", "engine/api", "conductor").forEach { assertNoImports(it, truthCore) }
        // Dirigenten kopplar ihop motor och SafetyGate men får inte se motorns implementation eller Babel.
        assertNoImports("conductor", listOf("se.pundaren.guide.engine.Default", "se.pundaren.guide.engine.LineBank", "se.pundaren.guide.engine.VoiceGuards", "se.pundaren.guide.babel"))
        // SafetyGate får se :engine:api (typen TailCandidate) men inte motorns implementation eller dess spärrlista.
        assertNoImports("safetygate", listOf("se.pundaren.guide.engine.Default", "se.pundaren.guide.engine.LineBank", "se.pundaren.guide.engine.VoiceGuards", "se.pundaren.guide.engine.TailContract"))
        assertNoImports("babel", listOf("se.pundaren.guide.truth", "se.pundaren.guide.safetygate", "se.pundaren.guide.engine"))
    }

    @Test fun `G-8 rena moduler har varken Android, Google eller nätverk`() {
        listOf("truth/api", "truth/core", "engine/api", "engine", "archive/api", "archive/core", "safetygate", "conductor", "babel").forEach {
            assertNoImports(it, network + android)
        }
    }

    @Test fun `motorn gör ingen I-O`() {
        assertNoImports("engine", listOf("java.io", "java.nio", "kotlin.io", "java.lang.System"))
        sources("engine").forEach { f ->
            val t = f.readText()
            listOf("System.nanoTime", "System.currentTimeMillis", "Instant.now").forEach {
                assertTrue("${f.name} använder $it", !t.contains(it))
            }
            // Bara seedad SplittableRandom; aldrig kotlin.random eller java.util.Random utan seed.
            assertTrue("${f.name} använder oseedad slump", !Regex("(?<!Splittable)Random\\(").containsMatchIn(t) && !t.contains("kotlin.random"))
        }
    }

    @Test fun `G-3 sanningsgränssnitten är bara läsning`() {
        listOf(TruthEventStream::class, TruthStateReader::class).forEach { k ->
            k.java.methods.forEach { m ->
                assertTrue("${k.simpleName}.${m.name} tar parametrar", m.parameterCount == 0)
                assertTrue("${k.simpleName}.${m.name} är inte en getter", m.name.startsWith("get"))
            }
            k.java.methods.forEach { m ->
                assertTrue("${m.name} returnerar muterbar flow", !m.returnType.name.contains("Mutable"))
            }
        }
        val src = sources("truth/api").joinToString("\n") { it.readText() }
        assertTrue("truth:api har publicering", !src.contains("MutableSharedFlow") && !src.contains("MutableStateFlow"))
        assertTrue("truth:api har publika var", !Regex("^\\s*(override\\s+)?var\\s", RegexOption.MULTILINE).containsMatchIn(src) && !Regex("\\(\\s*var\\s|,\\s*var\\s").containsMatchIn(src))
    }

    @Test fun `händelserna är oföränderliga värden`() {
        TruthEvent::class.sealedSubclasses.forEach { k ->
            assertTrue("${k.simpleName} är inte data class", k.isData)
            k.java.declaredMethods.filter { it.name.startsWith("set") && Modifier.isPublic(it.modifiers) }.let {
                assertTrue("${k.simpleName} har setter: $it", it.isEmpty())
            }
        }
        assertEquals("05 listar fjorton händelsetyper", 14, TruthEvent::class.sealedSubclasses.size)
    }

    @Test fun `G-4 TailCandidate bär inga sanningstyper och inga callbacks`() {
        val fields = TailCandidate::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        assertTrue(fields.isNotEmpty())
        fields.forEach { f ->
            val type = f.type.name
            assertTrue("${f.name}: $type är en sanningstyp", !type.startsWith("se.pundaren.guide.truth"))
            assertTrue("${f.name}: $type är en callback", !type.startsWith("kotlin.jvm.functions") && !type.startsWith("java.util.function"))
        }
    }

    @Test fun `motorns gränssnitt har ingen väg tillbaka till sanningen`() {
        val m = IllogicalEngine::class.java.methods.single()
        assertEquals("onEvent", m.name)
        assertEquals(List::class.java, m.returnType)
        assertTrue(m.genericReturnType.typeName.contains(TailCandidate::class.java.name))
    }

    @Test fun `G-9 och SG-23 SafetyGate returnerar bara beslut och har inga sanningsberoenden`() {
        SafetyGate::class.java.declaredMethods.filter { Modifier.isPublic(it.modifiers) }.forEach { m ->
            assertTrue("${m.name} returnerar ${m.returnType}", m.returnType == PresentationDecision::class.java || m.returnType.name.endsWith("GateState"))
            m.parameterTypes.forEach { p -> assertTrue(p == GateInput::class.java || p == TailCandidate::class.java) }
        }
        val gateBuild = root.resolve("safetygate/build.gradle.kts").readText()
        assertTrue(!gateBuild.contains("\":engine\"") && !gateBuild.contains(":truth:core"))
    }

    @Test fun `G-6 beroendegrafen i byggfilerna saknar förbjudna kanter`() {
        fun deps(module: String) = Regex("project\\(\"([^\"]+)\"\\)").findAll(root.resolve("$module/build.gradle.kts").readText()).map { it.groupValues[1] }.toSet()
        val forbidden = mapOf(
            "engine" to setOf(":truth:core", ":safetygate", ":voice", ":convoy:api", ":convoy:http", ":convoy:wss", ":babel"),
            "engine/api" to setOf(":truth:core", ":engine"),
            "safetygate" to setOf(":truth:core", ":engine", ":babel"),
            "babel" to setOf(":truth:api", ":truth:core", ":safetygate", ":engine", ":engine:api"),
            "truth/api" to setOf(":engine", ":engine:api", ":archive:api", ":safetygate", ":babel"),
            "archive/api" to setOf(":engine", ":truth:api"),
            // Etapp 4: sanningskärnan och dess Android-adaptrar känner inget av tramslagret.
            "truth/core" to setOf(":engine", ":engine:api", ":archive:api", ":archive:core", ":safetygate", ":conductor", ":babel", ":voice", ":app"),
            "truth/device" to setOf(":engine", ":engine:api", ":archive:api", ":archive:core", ":safetygate", ":conductor", ":babel", ":voice", ":app"),
            // Rösten ser bara sanningens port för besked.
            "voice" to setOf(":engine", ":engine:api", ":archive:api", ":truth:core", ":truth:device", ":safetygate", ":conductor", ":babel", ":app"),
            // Dirigenten läser sanningen genom :truth:api och når aldrig kärnan eller enheten.
            "conductor" to setOf(":truth:core", ":truth:device", ":voice", ":babel", ":app"),
        )
        forbidden.forEach { (m, bad) -> assertEquals("$m", emptySet<String>(), deps(m).intersect(bad)) }
    }

    @Test fun `inget Google-beroende någonstans i bygget`() {
        val files = root.walkTopDown()
            .onEnter { it.name !in setOf(".git", "build", ".gradle", "docs", "data", ".kotlin") }
            .filter { it.isFile && (it.extension in setOf("kts", "toml", "properties", "kt")) }
            .filterNot { it.name == "ArchitectureTest.kt" }
            // Rotens build.gradle.kts innehåller listan över förbjudna grupper; den kontrollen görs av verifyModuleGraph.
            .filterNot { it == root.resolve("build.gradle.kts") }
            // Etapp 2-prototypen: byggfilen och källprovet innehåller spärrlistorna själva. Där görs kontrollen
            // av verifieraIngenGoogle (löst klassväg) och tools/verkstad/07-kontrollera-apk.sh (dex i APK:n).
            .filterNot { it == root.resolve("prototyp/offline-kedja/app/build.gradle.kts") }
            .filterNot { it == root.resolve("prototyp/offline-kedja/app/src/test/java/se/pundaren/guide/prototyp/arkitektur/ArkitekturTest.kt") }
            .toList()
        val hits = files.filter { f -> val t = f.readText(); listOf("com.google", "gms", "firebase", "googleapis").any { t.contains(it) } }
        assertEquals(emptyList<File>(), hits)
    }
}
