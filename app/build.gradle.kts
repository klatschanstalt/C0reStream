import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
fun versionProperty(key: String, fallback: String): String =
    (project.findProperty("c0re.$key") as String?) ?: versionProps.getProperty(key) ?: fallback

val releaseStoreFile = System.getenv("C0RE_KEYSTORE_FILE")
    ?: System.getenv("ANDROID_KEYSTORE_FILE")
    ?: (project.findProperty("c0re.keystoreFile") as String?)
val releaseStorePassword = System.getenv("C0RE_KEYSTORE_PASSWORD")
    ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
    ?: (project.findProperty("c0re.keystorePassword") as String?)
val releaseKeyAlias = System.getenv("C0RE_KEY_ALIAS")
    ?: System.getenv("ANDROID_KEY_ALIAS")
    ?: (project.findProperty("c0re.keyAlias") as String?)
val releaseKeyPassword = System.getenv("C0RE_KEY_PASSWORD")
    ?: System.getenv("ANDROID_KEY_PASSWORD")
    ?: (project.findProperty("c0re.keyPassword") as String?)
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "pw.c0re.stream"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "pw.c0re.stream"
        minSdk = 30
        targetSdk = 37
        versionCode = versionProperty("versionCode", "4").toInt()
        versionName = versionProperty("versionName", "1.3.0")
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Media3: HLS/DASH/MPEG-TS over HTTP(S), MediaSession and background playback.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.work.runtime.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    // JVM XMLPull implementation so the hardened XMLTV parser is unit-testable.
    testImplementation(libs.kxml2)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
