import com.android.build.api.artifact.SingleArtifact
import org.gradle.process.CommandLineArgumentProvider
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Signing material lives in a git-ignored file at the repository root (RB-02). Without it the release build refuses to
// package an APK: it never falls back to the debug key.
val keystorePropertiesFile = rootProject.layout.projectDirectory.file("keystore.properties").asFile
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigning = keystorePropertiesFile.isFile && releaseSigningKeys.all { !keystoreProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.tagforge.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tagforge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 615
        versionName = "1.1.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Static shortcuts need the real package, suffix included (resources get no manifest placeholders).
            resValue("string", "shortcut_target_package", "com.tagforge.app.debug")
        }
        release {
            resValue("string", "shortcut_target_package", "com.tagforge.app")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Null when keystore.properties is missing: the guard below then stops packaging with a clear message.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "es")
    }

    lint {
        // Hardcoded user-visible text is a build error (rule G1).
        error += "HardcodedText"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// RB-02: packaging a release without the keystore must fail loudly, never produce an unsigned or debug-signed APK.
tasks.matching { it.name == "assembleRelease" || it.name == "packageRelease" || it.name == "bundleRelease" }.configureEach {
    // Plain values, not script properties: the configuration cache cannot serialize references to the script object.
    val signingReady = hasReleaseSigning
    val keystorePath = keystorePropertiesFile.path
    doFirst {
        if (!signingReady) {
            throw GradleException(
                "Cannot build a release: $keystorePath is missing or incomplete (keystore.properties). " +
                    "Copy keystore.properties.example to keystore.properties, fill in storeFile, storePassword, keyAlias and keyPassword, " +
                    "and create the keystore as described in dist/RELEASE.md. The debug key is never used for a release.",
            )
        }
    }
}

// RB-04: the unit tests audit the MERGED release manifest, not the source one.
class ReleaseManifestArgument(
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) val manifest: Provider<RegularFile>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("-Dtagforge.releaseManifest=${manifest.get().asFile.absolutePath}")
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val merged = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        // RB-03: the signed APK and its R8 mapping travel together under apks/ (git-ignored). The APK artifact comes from
        // the package task, which refuses to run without keystore.properties.
        val archiveName = "tagforge-1.1.5"
        tasks.register<Copy>("archiveRelease") {
            group = "distribution"
            description = "Copies the signed release APK and its R8 mapping to apks/ (needs keystore.properties)."
            from(variant.artifacts.get(SingleArtifact.APK)) {
                include("*.apk")
                rename { "$archiveName.apk" }
            }
            from(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)) { rename { "$archiveName-mapping.txt" } }
            into(rootProject.layout.projectDirectory.dir("apks"))
        }
        tasks.withType<Test>().configureEach {
            jvmArgumentProviders.add(ReleaseManifestArgument(merged))
        }
    }
}

// Room exports its schema JSON on every build; the files are committed so every version change needs a migration.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    // Live QR scanning (UX-B): preview + frame analysis only; the camera permission is asked when the user taps Scan.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
