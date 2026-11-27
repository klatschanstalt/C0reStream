import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
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
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            // No cookie jar is used; OkHttp's public-suffix table is unnecessary.
            "assets/PublicSuffixDatabase.list",
            // CI and modern emulators use x86_64; 32-bit x86 devices are unsupported.
            "lib/x86/**",
        )
    }
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
    implementation(libs.zxing)
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

// Signs the non-executable resource manifest with the existing production key. No new key is
// created and the signature file is published only together with a signed APK.
tasks.register("signResourceManifest") {
    doLast {
        val storeFile = releaseStoreFile ?: error("Release keystore is not configured")
        val storePassword = releaseStorePassword ?: error("Release store password is not configured")
        val alias = releaseKeyAlias ?: error("Release key alias is not configured")
        val keyPassword = releaseKeyPassword ?: error("Release key password is not configured")
        val keystore = KeyStore.getInstance("PKCS12").apply {
            file(storeFile).inputStream().use { load(it, storePassword.toCharArray()) }
        }
        val privateKey = keystore.getKey(alias, keyPassword.toCharArray()) as? PrivateKey
            ?: error("Release private key unavailable")
        val manifestBytes = rootProject.file("resources/manifest.json").readBytes()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(manifestBytes)
            sign()
        }
        rootProject.file("resources/manifest.sig").writeText(
            signature.joinToString("") { "%02x".format(it) },
        )
    }
}
