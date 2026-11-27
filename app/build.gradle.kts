import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
// version.properties is the single source of truth. Missing or malformed metadata must fail
// the build instead of silently producing a wrongly labelled release.
fun requiredVersion(key: String): String =
    versionProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("Missing required '$key' in version.properties")

val projectVersionCode = requiredVersion("versionCode").toIntOrNull()
    ?: throw GradleException("Invalid 'versionCode' in version.properties: expected a positive integer")
if (projectVersionCode <= 0) {
    throw GradleException("Invalid 'versionCode' in version.properties: expected a positive integer")
}
val projectVersionName = requiredVersion("versionName")
if (!projectVersionName.matches(Regex("""\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?"""))) {
    throw GradleException("Invalid 'versionName' in version.properties: expected MAJOR.MINOR.PATCH")
}

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
        versionCode = projectVersionCode
        versionName = projectVersionName
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
    implementation(libs.material3.windowsize)
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
    implementation(libs.media3.rtsp)

    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.work.runtime.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.zxing)
    implementation(libs.kotlinx.coroutines.android)

    // Media3 pulls in Guava transitively. Pin the Android flavour above the fix for
    // GHSA-xxph-c9ww-hj94 (deserialization OOM), without adding a direct runtime dependency.
    constraints {
        implementation(libs.guava) {
            because("GHSA-xxph-c9ww-hj94: Guava < 33.7.2 allows unbounded deserialization allocation")
        }
    }

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

tasks.withType<Test>().configureEach {
    System.getProperty("c0re.longstream")?.let { systemProperty("c0re.longstream", it) }
}

// Signs/verifies the non-executable resource manifest with the existing production key. No new
// key is created and the signature file is published only together with a signed APK.
fun loadReleaseKeyStore(): KeyStore {
    val storeFile = releaseStoreFile ?: error("Release keystore is not configured")
    val storePassword = releaseStorePassword ?: error("Release store password is not configured")
    return KeyStore.getInstance("PKCS12").apply {
        file(storeFile).inputStream().use { load(it, storePassword.toCharArray()) }
    }
}

tasks.register("signResourceManifest") {
    doLast {
        val keyPassword = releaseKeyPassword ?: error("Release key password is not configured")
        val alias = releaseKeyAlias ?: error("Release key alias is not configured")
        val privateKey = loadReleaseKeyStore().getKey(alias, keyPassword.toCharArray()) as? PrivateKey
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

tasks.register("verifyResourceManifest") {
    doLast {
        val alias = releaseKeyAlias ?: error("Release key alias is not configured")
        val certificate = loadReleaseKeyStore().getCertificate(alias)
            ?: error("Release certificate unavailable")
        val hex = rootProject.file("resources/manifest.sig").readText().trim()
        require(hex.length % 2 == 0 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Resource manifest signature is not valid hexadecimal"
        }
        val signature = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val valid = Signature.getInstance("SHA256withRSA").run {
            initVerify(certificate.publicKey)
            update(rootProject.file("resources/manifest.json").readBytes())
            verify(signature)
        }
        check(valid) { "Resource manifest signature verification failed" }
    }
}
