import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val mapsApiKey = readMapsApiKey(rootProject.layout.projectDirectory.asFile)

android {
    namespace = "se.pundaren.guide"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = "se.pundaren.guide"
        minSdk {
            version = release(26)
        }
        targetSdk {
            version = release(36)
        }
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    sourceSets.named("main") {
        assets.directories.add(layout.buildDirectory.dir("generated/guideAssets").get().asFile.absolutePath)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val bundleGuideAssets = tasks.register<Copy>("bundleGuideAssets") {
    from(rootProject.file("data/geojson/stockholms-lan")) {
        include("*.geojson")
    }
    from(rootProject.file("data/quotes")) {
        include("*.json")
    }
    from(rootProject.file("data/babel-stoner")) {
        include("*.json")
    }
    from(rootProject.file("data/lexicon")) {
        include("*.json")
    }
    into(layout.buildDirectory.dir("generated/guideAssets"))
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.matching { task ->
    task.name == "preBuild" || (task.name.startsWith("merge") && task.name.endsWith("Assets"))
}.configureEach {
    dependsOn(bundleGuideAssets)
}

dependencies {
    implementation(project(":engine"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.maps.compose)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
}

fun readMapsApiKey(root: File): String {
    fun load(name: String): Properties {
        val properties = Properties()
        val file = root.resolve(name)
        if (file.exists()) {
            file.inputStream().use { properties.load(it) }
        }
        return properties
    }
    val local = load("local.properties").getProperty("MAPS_API_KEY")
    val fallback = load("local.defaults.properties").getProperty("MAPS_API_KEY")
    return (local ?: fallback ?: "")
        .trim()
        .lineSequence()
        .firstOrNull()
        .orEmpty()
        .replace("\"", "")
        .replace("$", "")
}
