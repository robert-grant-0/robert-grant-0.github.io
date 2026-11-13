plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the workflow run number so every build installs as an update.
val buildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.toIntOrNull() ?: 1

// Optional release signing key, supplied by CI from repository secrets (never committed).
// Without it the release APK is signed with the standard debug key.
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
val releaseKeystore = env("BMS_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }

// `-Pscreenshots` renders the screens to PNG with Robolectric (see src/screenshotTest).
// Off by default so the normal build never depends on it.
val screenshots = providers.gradleProperty("screenshots").isPresent

android {
    namespace = "io.github.robertgrant0.bmsmonitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.robertgrant0.bmsmonitor"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = env("BMS_KEYSTORE_PASSWORD")
                keyAlias = env("BMS_KEY_ALIAS")
                keyPassword = env("BMS_KEY_PASSWORD") ?: env("BMS_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    if (screenshots) {
        testOptions.unitTests.isIncludeAndroidResources = true
        // Render through Robolectric's native hardware pipeline so Compose graphics layers draw.
        testOptions.unitTests.all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
        sourceSets.getByName("test").java.srcDir("src/screenshotTest/java")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    if (screenshots) {
        testImplementation(composeBom)
        testImplementation("androidx.compose.ui:ui-test-junit4")
        testImplementation("org.robolectric:robolectric:4.13")
        debugImplementation("androidx.compose.ui:ui-test-manifest")
    }
}
