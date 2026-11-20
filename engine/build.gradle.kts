import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.moshi)
    implementation(libs.coroutines.core)
    ksp(libs.moshi.codegen)
    testImplementation(libs.junit)
}

tasks.named<Test>("test") {
    useJUnit()
    systemProperty("guide.data", rootProject.projectDir.resolve("data").absolutePath)
}
