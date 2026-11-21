// :voice – en talmotor, två kanaler (03, 4.4). Kanal B (besked) avbryter alltid kanal S (svans).
// Ser bara sanningens utgående port InstructionVoice; känner varken Illogical Engine eller sanningskärnan.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "se.pundaren.guide.voice"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":truth:api"))
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
