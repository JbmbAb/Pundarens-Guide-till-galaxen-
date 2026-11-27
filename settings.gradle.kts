// Ett bygge för hela appen sedan etapp 4: rena Kotlin-moduler (JVM) och Android-moduler.
// Modulgränserna i 03 kontrolleras av verifyModuleGraph i rotens build.gradle.kts.
pluginManagement {
    repositories {
        google()            // bara byggverktyg (Android Gradle Plugin); inget av detta hamnar i appen
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()            // androidx (AOSP-bibliotek); Googles tjänster spärras av verifyModuleGraph
        mavenCentral()
    }
}

rootProject.name = "pundarens-guide"

// Rena Kotlin-moduler (ingen Android, inget nätverk).
include(
    ":truth:api",
    ":truth:core",
    ":engine:api",
    ":engine",
    ":archive:api",
    ":archive:core",
    ":safetygate",
    ":conductor",
    ":babel",
    ":convoy:api",
    ":convoy:http",
    ":encyclopedia",
    ":online",
    ":verification:architecture",
)

// Android-moduler.
include(
    ":truth:device",
    ":voice",
    ":app",
)
