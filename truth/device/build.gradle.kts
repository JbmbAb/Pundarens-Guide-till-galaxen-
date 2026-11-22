// :truth:device – sanningskärnans Android-adaptrar: valhalla-mobile (ruttning på enheten),
// Ferrostar (navigering), GNSS via AOSP LocationManager och batteri. Matar :truth:core.
// Känner varken Illogical Engine, SafetyGate, rösten eller skärmen (03, G-1).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "se.pundaren.guide.truth.device"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // krävs av Ferrostar core 0.59.0 (AAR-metadata)
    }
}

dependencies {
    api(project(":truth:api"))
    api(project(":truth:core"))
    // Ruttning på enheten (MIT). 0.6.6 bygger Valhalla 3.9.2.
    implementation(libs.valhalla.mobile)
    implementation(libs.valhalla.models)
    implementation(libs.valhalla.models.config)
    // Navigeringskärna (BSD-3). Bara core: ingen UI och ingen artefakt för Play-tjänster.
    implementation(libs.ferrostar.core)
    implementation(libs.coroutines.android)
    coreLibraryDesugaring(libs.desugar)
    testImplementation(libs.junit)
}
