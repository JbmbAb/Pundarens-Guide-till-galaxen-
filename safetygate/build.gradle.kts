// :safetygate – deterministisk presentationsregel (06). Ren Kotlin.
dependencies {
    "implementation"(project(":truth:api"))
    "implementation"(project(":engine:api"))
    "implementation"(project(":archive:api"))
    "testImplementation"(libs.junit)
}
