plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Generates the Room database code.
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.logistics.driver"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Same id the Expo app used; it was never published, so nothing is lost.
        applicationId = "com.logistics.driver"
        minSdk = 26
        // 36 = Android 16, what Google Play requires from 31 Aug 2026. We
        // sideload, but there's no reason to target lower.
        targetSdk = 36
        // The version lives in version.txt, which release-please bumps. The
        // versionCode is derived from it (major * 10000 + minor * 100 + patch),
        // so it goes up with every release: Android only installs an APK over
        // the old one if versionCode is higher (see docs/android-driver-app.md).
        val version = rootProject.file("version.txt").readText().trim()
        versionName = version
        versionCode = versionCodeOf(version)
        // Overridable for local demos of the in-app update prompt
        // (scripts/demo-update.sh); releases use version.txt.
        providers.gradleProperty("driver.versionCode").orNull?.let { versionCode = it.toInt() }
        providers.gradleProperty("driver.versionName").orNull?.let { versionName = it }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from the environment, so the keystore and its
    // passwords never enter git (scripts/build-release.sh sets these). Without
    // DRIVER_KEYSTORE, assembleRelease still builds, unsigned, as CI does.
    val keystore = providers.environmentVariable("DRIVER_KEYSTORE").orNull
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("DRIVER_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("DRIVER_KEY_ALIAS").getOrElse("driver")
                keyPassword = providers.environmentVariable("DRIVER_KEY_PASSWORD")
                    .orElse(providers.environmentVariable("DRIVER_KEYSTORE_PASSWORD")).get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG decides whether a plain http:// server address is
        // allowed (debug builds only, for testing against a local backend).
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.location)
    // QR scanner drawn by Google Play services: no camera permission or
    // scanning UI of our own.
    implementation(libs.play.services.code.scanner)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests (src/androidTest), run on an emulator or phone with
    // ./gradlew connectedDebugAndroidTest.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/** "1.2.3" (or "1.2.3-anything") -> 10203. Each part must stay below 100. */
fun versionCodeOf(version: String): Int {
    val parts = version.substringBefore('-').split('.').map { it.toInt() }
    require(parts.size == 3 && parts.all { it in 0..99 }) { "version.txt must be X.Y.Z with parts 0-99, got $version" }
    return parts[0] * 10000 + parts[1] * 100 + parts[2]
}
