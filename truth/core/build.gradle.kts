// :truth:core – sanningskärnans logik. Ren Kotlin: ingen Android, inget nätverk, ingen tramskod.
// Avvikelse E4-D1 (etapp4-protokollet): 03 beskriver :truth:core som Android-bibliotek. Logiken ligger
// här i ren Kotlin så att den kan testas på JVM; Android-adaptrarna ligger i :truth:device.
dependencies {
    "api"(project(":truth:api"))
    "implementation"(libs.serialization.json)
    "testImplementation"(libs.junit)
}
