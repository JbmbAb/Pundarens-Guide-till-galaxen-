import java.util.zip.ZipFile

plugins {
    id("com.android.application")
}

// Offlinepaketet byggs i verkstaden (tools/verkstad) och checkas inte in.
// Ange katalogen med -PpaketKatalog=/sökväg/till/paket/<version>; den läggs som tillgång i APK:n.
val paketKatalog: String? = providers.gradleProperty("paketKatalog").orNull

android {
    namespace = "se.pundaren.guide.prototyp"
    compileSdk = 36
    defaultConfig {
        applicationId = "se.pundaren.guide.prototyp.offlinekedja"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-etapp2"
        // Målenheten SM-X900 är arm64. Bara den ABI:n packas. För emulatorprov i verkstaden:
        // -Pabi=x86_64 (ger en APK som INTE går att installera på målenheten).
        ndk { abiFilters += listOf(providers.gradleProperty("abi").orNull ?: "arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // krävs av Ferrostar core 0.59.0 (AAR-metadata)
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}

/** Kopierar offlinepaketet (manifest + filer) till APK:ns tillgångar under paket/. */
abstract class KopieraPaket : DefaultTask() {
    @get:InputDirectory @get:Optional abstract val kalla: DirectoryProperty
    @get:OutputDirectory abstract val utKatalog: DirectoryProperty
    @TaskAction fun kopiera() {
        val ut = utKatalog.get().asFile.resolve("paket")
        ut.deleteRecursively(); ut.mkdirs()
        kalla.orNull?.asFile?.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(ut.resolve(it.name)) }
    }
}
val kopieraPaket = tasks.register<KopieraPaket>("kopieraPaket") {
    if (paketKatalog != null) kalla.set(file(paketKatalog))
}
androidComponents {
    onVariants { variant ->
        if (paketKatalog != null) variant.sources.assets?.addGeneratedSourceDirectory(kopieraPaket, KopieraPaket::utKatalog)
    }
}

dependencies {
    // Ruttning på enheten (MIT). 0.6.6 bygger Valhalla 3.9.2.
    implementation("io.github.rallista:valhalla-mobile:0.6.6")
    implementation("io.github.rallista:valhalla-models:0.6.0")
    implementation("io.github.rallista:valhalla-models-config:0.6.0")
    // Navigeringskärna (BSD-3). Bara core: ingen UI, ingen google-play-services-artefakt.
    implementation("com.stadiamaps.ferrostar:core:0.59.0")
    // Kartvisning (BSD-2), läser pmtiles:// sedan 11.7.0.
    implementation("org.maplibre.gl:android-sdk:13.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517") // Androids org.json finns inte i JVM-test
}

// ---------------------------------------------------------------------------------------------
// Beroendespärr: ingen Google-tjänst får finnas i appen (F-06, startprompten).
// Kontrollen löser appens körtidsklassväg och stoppar bygget vid en förbjuden grupp.
// Alla andra com.google.*-artefakter måste stå i tillåtelselistan med motivering, annars stopp.
// ---------------------------------------------------------------------------------------------
val forbjudnaGrupper = listOf(
    "com.google.android.gms",          // Play-tjänster
    "com.google.firebase",             // Firebase
    "com.google.crypto.tink",          // Tink
    "com.google.android.libraries.maps", "com.google.maps", "com.google.android.libraries.places", // Google Maps
    "com.google.android.play",         // Play Core
    "com.google.mlkit", "com.google.android.datatransport", "com.google.android.libraries",
)
// Grupp:modul → motivering. Varje rad är ett öppet ägarbeslut (etapp2-protokollet, E2-D3).
// Båda är fristående öppna Java-bibliotek (Apache-2.0) utan nätverk, konto eller tjänst.
val tillatnaGoogleModuler = mapOf(
    "com.google.code.gson:gson" to "JSON-läsare som MapLibres GeoJSON-modul (android-sdk-geojson 6.0.1) kräver",
    "com.google.guava:listenablefuture" to "tom markörartefakt (ett gränssnitt) som androidx.concurrent kräver",
)

val verifieraIngenGoogle by tasks.registering {
    group = "verification"
    description = "Stoppar bygget om appens körtidsberoenden innehåller Google-tjänster"
    val klassvagar = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
    val rapport = layout.buildDirectory.file("rapporter/beroenden.txt")
    outputs.file(rapport)
    outputs.upToDateWhen { false }
    doLast {
        val rader = mutableListOf<String>()
        val fel = mutableListOf<String>()
        for (namn in klassvagar) {
            val komponenter = configurations.getByName(namn).incoming.resolutionResult.allComponents
                .mapNotNull { it.moduleVersion }.map { "${it.group}:${it.name}:${it.version}" }.sorted()
            rader += "== $namn (${komponenter.size} moduler)"
            rader += komponenter
            for (k in komponenter) {
                val (grupp, modul) = k.split(":")
                if (forbjudnaGrupper.any { grupp == it || grupp.startsWith("$it.") }) fel += "$namn: FÖRBJUDEN $k"
                else if (grupp.startsWith("com.google") && "$grupp:$modul" !in tillatnaGoogleModuler)
                    fel += "$namn: com.google-modul som inte är granskad: $k"
            }
        }
        rapport.get().asFile.apply { parentFile.mkdirs(); writeText(rader.joinToString("\n") + "\n") }
        if (fel.isNotEmpty()) throw GradleException("Beroendespärren stoppade bygget:\n" + fel.joinToString("\n"))
        val undantag = rader.filter { r -> tillatnaGoogleModuler.keys.any { r.startsWith("$it:") } }.toSortedSet()
        println("verifieraIngenGoogle: OK. Inga förbjudna moduler. Granskade undantag: $undantag. Rapport: ${rapport.get().asFile}")
    }
}
tasks.named("preBuild") { dependsOn(verifieraIngenGoogle) }
tasks.matching { it.name == "check" }.configureEach { dependsOn(verifieraIngenGoogle) }
