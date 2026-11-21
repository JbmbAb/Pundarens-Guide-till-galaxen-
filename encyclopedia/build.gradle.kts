// :encyclopedia – Galactic Encyclopedia (etapp 7): egna fältanteckningar och arkivets godkända lexikon.
// Ren Kotlin. Ingen genererad text: det enda som läggs till är det användaren själv skriver.
dependencies {
    "implementation"(project(":truth:api"))
    "implementation"(project(":archive:api"))
    "testImplementation"(libs.junit)
    "testImplementation"(project(":archive:core"))
}
