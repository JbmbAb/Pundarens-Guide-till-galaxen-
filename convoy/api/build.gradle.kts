// :convoy:api – Convoy för två (07): gränssnittet mot transporten och protokollets logik ovanför den.
// Ren Kotlin. Vet inget om kartan, Illogical Engine eller någon server. Inget nätverksbibliotek:
// transporten ligger i :convoy:http.
dependencies {
    "api"(libs.coroutines.core)
    "testImplementation"(libs.junit)
}
