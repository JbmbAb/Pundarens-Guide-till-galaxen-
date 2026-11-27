// :online – de frivilliga nättjänsterna: Trafiklab (ResRobot och realtid) och Claude. Ren Kotlin.
// Anropen görs med vanliga HTTPS-anrop ur JDK:n, inget nätverksbibliotek. Nycklarna kommer in som
// parametrar från appen och sparas aldrig här. Utan nät eller nyckel fungerar appen som förut.
dependencies {
    "implementation"(libs.serialization.json)
    "testImplementation"(libs.junit)
}
