// :archive:core – ren Kotlin. Import av data/ till ArchiveEntry, skrivskydd på modellnivå
// och SQL-schemat ur 04. Valet Room eller SQLDelight (A-13) är uppskjutet.
dependencies {
    "api"(project(":archive:api"))
    "implementation"(libs.serialization.json)
    "testImplementation"(libs.junit)
    // Bara i test: kör triggerna i 04 mot vanlig SQLite (A-13, delvis).
    "testImplementation"(libs.sqlite.jdbc)
}
