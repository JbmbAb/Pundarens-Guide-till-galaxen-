// Etapp 1 (motsvarar innehållet i etapp 2 i 09): bara rena Kotlin-moduler.
// Ingen Android, ingen karta, ingen ruttning, inget Google-beroende.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "pundarens-guide"

include(
    ":truth:api",
    ":engine:api",
    ":engine",
    ":archive:api",
    ":archive:core",
    ":safetygate",
    ":babel",
    ":verification:architecture",
)
