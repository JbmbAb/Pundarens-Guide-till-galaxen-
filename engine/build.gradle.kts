// :engine – Illogical Engine. Ren Kotlin utan Android, nätverk eller I/O i onEvent.
dependencies {
    "implementation"(project(":engine:api"))
    "implementation"(project(":truth:api"))
    "implementation"(project(":archive:api"))
    "testImplementation"(libs.junit)
    "testImplementation"(project(":archive:core"))
}
