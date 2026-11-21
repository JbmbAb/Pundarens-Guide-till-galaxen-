import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

/** Android-moduler. Alla andra moduler är ren Kotlin (JVM) utan Android och utan nätverk. */
val androidModules = setOf(":truth:device", ":voice", ":app")

// ---------------------------------------------------------------------------
// G-6 (03): beroendegrafen kontrolleras vid varje bygge. Förbjudna kanter
// fäller bygget. Listan följer tabellen i 03, avsnitt 2, med avvikelserna i
// etapp1-protokollet (D-1) och etapp4-protokollet (E4-D1–E4-D3).
// ---------------------------------------------------------------------------
val allowedProjectEdges: Map<String, Set<String>> = mapOf(
    ":truth:api" to emptySet(),
    ":archive:api" to emptySet(),
    // Sanningskärnans logik, ren Kotlin. Känner bara till sanningstyperna.
    ":truth:core" to setOf(":truth:api"),
    // Avvikelse D-1 (etapp1-protokoll): :engine:api får se :archive:api för
    // ursprungsklass, godkännande och synlighet i TailCandidate.
    ":engine:api" to setOf(":truth:api", ":archive:api"),
    ":engine" to setOf(":engine:api", ":truth:api", ":archive:api"),
    ":archive:core" to setOf(":archive:api"),
    ":safetygate" to setOf(":truth:api", ":engine:api", ":archive:api"),
    // Svansdirigenten: händelser -> motor -> SafetyGate -> uppspelning. Läser sanningen, skriver aldrig.
    ":conductor" to setOf(":truth:api", ":engine:api", ":safetygate", ":archive:api"),
    ":babel" to setOf(":archive:api"),
    // Convoy (07): protokollets logik ovanför transporten. Känner varken sanningen, motorn eller arkivet.
    ":convoy:api" to emptySet(),
    // Transporten mot reläet: vanliga HTTP-anrop ur JDK:n, inget nätverksbibliotek.
    ":convoy:http" to setOf(":convoy:api"),
    // Encyklopedin: egna fältanteckningar (med plats) och arkivets godkända lexikon.
    ":encyclopedia" to setOf(":truth:api", ":archive:api"),
    // Sanningskärnans Android-adaptrar: valhalla-mobile, Ferrostar, position, batteri.
    ":truth:device" to setOf(":truth:api", ":truth:core"),
    // Rösten: en talmotor, två kanaler. Ser bara sanningens port för besked.
    ":voice" to setOf(":truth:api"),
    // Kompositionsroten ser allt (03, kvarstående risk R-07).
    ":app" to setOf(
        ":truth:api", ":truth:core", ":truth:device", ":engine:api", ":engine", ":archive:api", ":archive:core",
        ":safetygate", ":conductor", ":voice", ":babel", ":encyclopedia",
    ),
    // Testmodulen får se allt rent, men bara i testkonfigurationer.
    ":verification:architecture" to setOf(
        ":truth:api", ":truth:core", ":engine:api", ":engine", ":archive:api", ":archive:core", ":safetygate",
        ":conductor", ":babel",
    ),
)

// Kanter som bara får finnas i testkonfigurationer (testdata importeras via :archive:core).
val allowedTestOnlyEdges: Map<String, Set<String>> = mapOf(
    ":engine" to setOf(":archive:core"),
    ":babel" to setOf(":archive:core"),
    ":encyclopedia" to setOf(":archive:core"),
    ":conductor" to setOf(":engine", ":archive:core"),
)

// Rena moduler: externa beroenden som får finnas i produktionskod (runtimeClasspath).
val allowedExternalGroups = setOf(
    "org.jetbrains.kotlin",
    "org.jetbrains.kotlinx",
    "org.jetbrains",
)
val forbiddenGroupPrefixes = listOf("com.google", "com.android", "androidx", "com.squareup.okhttp", "io.ktor")

// Android-moduler: ingen Google-tjänst får finnas i appen (F-06, startprompten). Samma listor som
// etapp 2-prototypens verifieraIngenGoogle. Ägarbeslut 2026-11-21 06.15 (E2-D3): förbudet gäller
// Googles TJÄNSTER; gson och listenablefuture får vara kvar. Nya rader kräver nytt ägarbeslut.
val forbiddenServiceGroups = listOf(
    "com.google.android.gms",          // Play-tjänster
    "com.google.firebase",             // Firebase
    "com.google.crypto.tink",          // Tink
    "com.google.android.libraries.maps", "com.google.maps", "com.google.android.libraries.places", // Google Maps
    "com.google.android.play",         // Play Core
    "com.google.mlkit", "com.google.android.datatransport", "com.google.android.libraries",
)
val reviewedOpenLibraries = mapOf(
    "com.google.code.gson:gson" to "JSON-läsare som MapLibres GeoJSON-modul (android-sdk-geojson 6.0.1) kräver",
    "com.google.guava:listenablefuture" to "tom markörartefakt (ett gränssnitt) som androidx.concurrent kräver",
)

subprojects {
    val module = this
    if (!module.buildFile.exists()) return@subprojects   // behållare som :truth och :archive
    val isAndroid = module.path in androidModules

    if (!isAndroid) {
        apply(plugin = "org.jetbrains.kotlin.jvm")

        // Ingen JDK-verktygskedja krävs: koden byggs för Java 17 med den JDK som kör Gradle
        // (JDK 17 i verkstaden, Android Studios JDK 21 på ägarens dator).
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<Test>().configureEach {
            useJUnit()
            systemProperty("guide.repoRoot", rootProject.projectDir.absolutePath)
            testLogging {
                events("passed", "skipped", "failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStandardStreams = false
            }
        }
    }

    // Varje modul kontrollerar sina egna kanter och sina egna lösta beroenden (i sitt eget projekt,
    // som Gradle 9 kräver). Rotens verifyModuleGraph samlar ihop rapporterna.
    val verifyEdges = tasks.register("verifyModuleEdges") {
        group = "verification"
        description = "G-6: fäller bygget om modulen har en förbjuden kant eller ett förbjudet externt beroende."
        val reportFile = module.layout.buildDirectory.file("reports/module-graph.txt")
        outputs.file(reportFile)
        outputs.upToDateWhen { false }
        doLast {
            val violations = mutableListOf<String>()
            val report = StringBuilder()
            val allowed = allowedProjectEdges[module.path]
            if (allowed == null) {
                violations += "${module.path}: modulen saknas i allowedProjectEdges"
            } else {
                module.configurations.forEach { conf ->
                    conf.dependencies.withType(ProjectDependency::class.java).forEach { dep ->
                        val target = dep.path
                        // Android lägger till modulen själv i sina testkonfigurationer (koden under test).
                        if (target == module.path) return@forEach
                        report.append("${module.path} -[${conf.name}]-> $target\n")
                        val isTestConf = conf.name.startsWith("test") || conf.name.startsWith("androidTest") ||
                            conf.name.contains("UnitTest") || conf.name.contains("AndroidTest")
                        val testOnly = if (isTestConf) allowedTestOnlyEdges[module.path].orEmpty() else emptySet()
                        if (target !in allowed && target !in testOnly) violations += "${module.path} -> $target (${conf.name}) är förbjuden"
                    }
                }
            }
            if (!isAndroid) {
                listOf("runtimeClasspath", "testRuntimeClasspath").mapNotNull { module.configurations.findByName(it) }.forEach { conf ->
                    conf.incoming.resolutionResult.allComponents.mapNotNull { it.moduleVersion }.forEach { id ->
                        val group = id.group
                        if (group.startsWith("pundarens-guide")) return@forEach
                        if (forbiddenGroupPrefixes.any { group.startsWith(it) }) {
                            violations += "${module.path} [${conf.name}] har förbjudet beroende $group:${id.name}"
                        }
                        if (conf.name == "runtimeClasspath" && group !in allowedExternalGroups) {
                            violations += "${module.path} [runtimeClasspath] har ej godkänt externt beroende $group:${id.name}"
                        }
                        report.append("${module.path} [${conf.name}] $group:${id.name}:${id.version}\n")
                    }
                }
            } else {
                listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").mapNotNull { module.configurations.findByName(it) }.forEach { conf ->
                    val components = conf.incoming.resolutionResult.allComponents
                        .mapNotNull { it.moduleVersion }.map { Triple(it.group, it.name, it.version) }.sortedBy { "${it.first}:${it.second}" }
                    report.append("== ${module.path} ${conf.name} (${components.size} moduler)\n")
                    for ((group, name, version) in components) {
                        report.append("${module.path} [${conf.name}] $group:$name:$version\n")
                        if (forbiddenServiceGroups.any { group == it || group.startsWith("$it.") }) {
                            violations += "${module.path} [${conf.name}] FÖRBJUDEN Google-tjänst $group:$name:$version"
                        } else if (group.startsWith("com.google") && "$group:$name" !in reviewedOpenLibraries) {
                            violations += "${module.path} [${conf.name}] com.google-modul som inte är granskad: $group:$name:$version"
                        }
                    }
                }
            }
            val out = reportFile.get().asFile
            out.parentFile.mkdirs()
            out.writeText(report.toString() + "\nÖverträdelser: ${violations.size}\n" + violations.joinToString("\n"))
            if (violations.isNotEmpty()) throw GradleException("G-6 överträdelser:\n" + violations.joinToString("\n"))
        }
    }
    tasks.matching { it.name == "check" || (isAndroid && it.name == "preBuild") }.configureEach { dependsOn(verifyEdges) }
}

val verifyModuleGraph by tasks.registering {
    group = "verification"
    description = "G-6: fäller bygget om en förbjuden modulkant eller ett förbjudet externt beroende finns."
    val modules = subprojects.filter { it.buildFile.exists() }
    dependsOn(modules.map { "${it.path}:verifyModuleEdges" })
    val parts = modules.map { it.layout.buildDirectory.file("reports/module-graph.txt") }
    val out = layout.buildDirectory.file("reports/module-graph.txt")
    outputs.file(out)
    outputs.upToDateWhen { false }
    doLast {
        val text = parts.map { it.get().asFile }.filter { it.isFile }.joinToString("\n") { it.readText().trim() }
        out.get().asFile.apply { parentFile.mkdirs(); writeText(text + "\n") }
        println("verifyModuleGraph: 0 överträdelser i ${parts.size} moduler. Rapport: ${out.get().asFile.relativeTo(rootDir)}")
    }
}

tasks.register("check") {
    group = "verification"
    dependsOn(verifyModuleGraph)
    dependsOn(subprojects.filter { it.buildFile.exists() }.map { "${it.path}:check" })
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
