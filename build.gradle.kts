import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(17)
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

// ---------------------------------------------------------------------------
// G-6 (03): beroendegrafen kontrolleras vid varje bygge. Förbjudna kanter
// fäller bygget. Listan följer tabellen i 03, avsnitt 2.
// ---------------------------------------------------------------------------
val allowedProjectEdges: Map<String, Set<String>> = mapOf(
    ":truth:api" to emptySet(),
    ":archive:api" to emptySet(),
    // Avvikelse D-1 (etapp1-protokoll): :engine:api får se :archive:api för
    // ursprungsklass, godkännande och synlighet i TailCandidate.
    ":engine:api" to setOf(":truth:api", ":archive:api"),
    ":engine" to setOf(":engine:api", ":truth:api", ":archive:api"),
    ":archive:core" to setOf(":archive:api"),
    ":safetygate" to setOf(":truth:api", ":engine:api", ":archive:api"),
    ":babel" to setOf(":archive:api"),
    // Testmodulen får se allt, men bara i testkonfigurationer.
    ":verification:architecture" to setOf(
        ":truth:api", ":engine:api", ":engine", ":archive:api", ":archive:core", ":safetygate", ":babel",
    ),
)

// Kanter som bara får finnas i testkonfigurationer (testdata importeras via :archive:core).
val allowedTestOnlyEdges: Map<String, Set<String>> = mapOf(
    ":engine" to setOf(":archive:core"),
    ":babel" to setOf(":archive:core"),
)

// Externa beroenden som får finnas i produktionskod (runtimeClasspath).
val allowedExternalGroups = setOf(
    "org.jetbrains.kotlin",
    "org.jetbrains.kotlinx",
    "org.jetbrains",
)
val forbiddenGroupPrefixes = listOf("com.google", "com.android", "androidx", "com.squareup.okhttp", "io.ktor")

val verifyModuleGraph by tasks.registering {
    group = "verification"
    description = "G-6: fäller bygget om en förbjuden modulkant eller ett förbjudet externt beroende finns."
    doLast {
        val violations = mutableListOf<String>()
        val report = StringBuilder()
        subprojects.filter { it.buildFile.exists() }.forEach { p ->
            val allowed = allowedProjectEdges[p.path]
            if (allowed == null) {
                violations += "${p.path}: modulen saknas i allowedProjectEdges"
                return@forEach
            }
            p.configurations.forEach { conf ->
                conf.dependencies.withType(ProjectDependency::class.java).forEach { dep ->
                    val target = dep.dependencyProject.path
                    report.append("${p.path} -[${conf.name}]-> $target\n")
                    val testOnly = if (conf.name.startsWith("test")) allowedTestOnlyEdges[p.path].orEmpty() else emptySet()
                    if (target !in allowed && target !in testOnly) violations += "${p.path} -> $target (${conf.name}) är förbjuden"
                }
            }
            listOf("runtimeClasspath", "testRuntimeClasspath").mapNotNull { p.configurations.findByName(it) }.forEach { conf ->
                conf.resolvedConfiguration.resolvedArtifacts.forEach { a ->
                    val id = a.moduleVersion.id
                    val group = id.group
                    if (forbiddenGroupPrefixes.any { group.startsWith(it) }) {
                        violations += "${p.path} [${conf.name}] har förbjudet beroende $group:${id.name}"
                    }
                    if (conf.name == "runtimeClasspath" && group !in allowedExternalGroups &&
                        !group.startsWith("pundarens-guide")
                    ) {
                        violations += "${p.path} [runtimeClasspath] har ej godkänt externt beroende $group:${id.name}"
                    }
                    report.append("${p.path} [${conf.name}] $group:${id.name}:${id.version}\n")
                }
            }
        }
        val out = layout.buildDirectory.file("reports/module-graph.txt").get().asFile
        out.parentFile.mkdirs()
        out.writeText(report.toString() + "\nÖverträdelser: ${violations.size}\n" + violations.joinToString("\n"))
        if (violations.isNotEmpty()) throw GradleException("G-6 överträdelser:\n" + violations.joinToString("\n"))
        println("verifyModuleGraph: 0 överträdelser. Rapport: ${out.relativeTo(rootDir)}")
    }
}

tasks.register("check") {
    group = "verification"
    dependsOn(verifyModuleGraph)
    dependsOn(subprojects.map { "${it.path}:check" })
}
