// Kastbar prototyp för etapp 2 (byggplanens etapp 3, grindarna G2 och G3): offlinekedjan
// valhalla-mobile → Ferrostar → MapLibre. Eget Gradle-bygge, skilt från rotprojektets rena Kotlin-moduler.
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
        google()            // androidx (AOSP-bibliotek); Play-tjänster och Firebase spärras i app/build.gradle.kts
        mavenCentral()
    }
}
rootProject.name = "offline-kedja"
include(":app")
