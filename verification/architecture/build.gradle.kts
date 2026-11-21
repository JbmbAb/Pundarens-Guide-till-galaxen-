// Arkitekturtester (03 G-1–G-9, 06 SG-23) och röstreglerna som körbara tester (12).
dependencies {
    "testImplementation"(libs.junit)
    "testImplementation"(libs.kotlin.reflect)
    "testImplementation"(project(":truth:api"))
    "testImplementation"(project(":truth:core"))
    "testImplementation"(project(":conductor"))
    "testImplementation"(project(":engine:api"))
    "testImplementation"(project(":engine"))
    "testImplementation"(project(":archive:api"))
    "testImplementation"(project(":archive:core"))
    "testImplementation"(project(":safetygate"))
    "testImplementation"(project(":babel"))
}
