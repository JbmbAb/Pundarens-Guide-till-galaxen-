// :app – kompositionsroten. Kopplar ihop sanningskärnan, rösten, Illogical Engine, SafetyGate och
// svansdirigenten, och ritar skärmen: sanningsremsan överst, kartan, svansytan och nödkortet.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Offlinepaketet byggs i verkstaden (tools/verkstad) och checkas inte in. Två sätt att få in det:
//  1. -PpaketKatalog=/sökväg/till/paket/<version> lägger det som tillgång i APK:n (en fil att installera).
//  2. adb push till Android/data/se.pundaren.guide/files/inkommande/ (liten APK, paketet för sig).
val paketKatalog: String? = providers.gradleProperty("paketKatalog").orNull

android {
    namespace = "se.pundaren.guide.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "se.pundaren.guide"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4-etapp4"
        // Målenheten SM-X900 är arm64. Bara den ABI:n packas. För emulatorprov: -Pabi=x86_64.
        ndk { abiFilters += listOf(providers.gradleProperty("abi").orNull ?: "arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // krävs av Ferrostar core 0.59.0 (AAR-metadata)
    }
    buildFeatures { compose = true }
    packaging { jniLibs { useLegacyPackaging = true } }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint {
        // NotificationPermission: gäller Ferrostars FerrostarForegroundService, som följer med biblioteket
        //   men aldrig startas här (navigeringen körs med skärmen tänd, utan förgrundstjänst).
        // ChromeOsAbiSupport: bara målenhetens ABI (arm64) packas, avsiktligt.
        // UseKtx: appen har inget beroende på androidx.core-ktx och behöver inget.
        disable += setOf("NotificationPermission", "ChromeOsAbiSupport", "UseKtx")
    }
}

/** Kopierar filer till en genererad tillgångskatalog under [underkatalog]. */
abstract class KopieraTillgangar : DefaultTask() {
    @get:InputFiles abstract val kallor: ConfigurableFileCollection
    @get:Input abstract val underkatalog: Property<String>
    @get:OutputDirectory abstract val utKatalog: DirectoryProperty
    @TaskAction fun kopiera() {
        val ut = utKatalog.get().asFile.resolve(underkatalog.get())
        ut.deleteRecursively(); ut.mkdirs()
        kallor.files.filter { it.isFile }.forEach { it.copyTo(ut.resolve(it.name)) }
    }
}

// Arkivet (citat, regler, lexikon, navigationskommentarer) har en enda källa: data/ i repot.
// Filerna följer med appen oförändrade och importeras vid start med ursprungsklass och godkännande.
val kopieraArkiv = tasks.register<KopieraTillgangar>("kopieraArkiv") {
    val data = rootProject.layout.projectDirectory.dir("data")
    kallor.from(
        data.file("quotes/quotes.json"), data.file("lexicon/lexicon.json"), data.file("babel-stoner/rules.json"),
        data.file("babel-stoner/examples.json"), data.file("babel-stoner/navigation_comments.json"),
    )
    underkatalog.set("arkiv")
}
val kopieraPaket = tasks.register<KopieraTillgangar>("kopieraPaket") {
    if (paketKatalog != null) kallor.from(fileTree(paketKatalog) { include("*") })
    underkatalog.set("paket")
}
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(kopieraArkiv, KopieraTillgangar::utKatalog)
        if (paketKatalog != null) variant.sources.assets?.addGeneratedSourceDirectory(kopieraPaket, KopieraTillgangar::utKatalog)
    }
}

dependencies {
    implementation(project(":truth:api"))
    implementation(project(":truth:core"))
    implementation(project(":truth:device"))
    implementation(project(":voice"))
    implementation(project(":engine:api"))
    implementation(project(":engine"))
    implementation(project(":archive:api"))
    implementation(project(":archive:core"))
    implementation(project(":safetygate"))
    implementation(project(":conductor"))
    implementation(project(":babel"))

    // Kartvisning (BSD-2), läser pmtiles:// sedan 11.7.0.
    implementation(libs.maplibre)
    implementation(libs.coroutines.android)

    // Compose (androidx, AOSP). Inga Google-tjänster; kontrolleras av verifyModuleEdges.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    coreLibraryDesugaring(libs.desugar)
    testImplementation(libs.junit)
}
