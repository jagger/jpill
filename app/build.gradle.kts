import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    id("com.google.devtools.ksp")
}

// Single source of truth for the app version (SemVer). Bump here, add a CHANGELOG.md entry, tag vX.Y.Z.
val appVersion = "0.12.0"

// Release signing: the keystore lives OUTSIDE the repository. Put its details in keystore.properties (gitignored)
// at the project root: storeFile, storePassword, keyAlias, keyPassword. Without it, assembleRelease is unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.jagger.timer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jagger.timer"
        minSdk = 35
        targetSdk = 35
        // 0.1.0 -> 100, 1.2.3 -> 10203: always increases with the version
        versionCode = appVersion.split(".").map { it.toInt() }.let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
    }
    // Exported Room schemas let MigrationTestHelper verify upgrades against real snapshots.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

// The What's new popup reads the changelog from the app itself (no network), so ship CHANGELOG.md as an asset.
// Copied at build time so the popup can never drift from the real changelog.
val changelogAssetsDir = layout.buildDirectory.dir("generated/changelogAssets")
val copyChangelog by tasks.registering(Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    into(changelogAssetsDir)
}
android.sourceSets["main"].assets.srcDir(changelogAssetsDir)
tasks.named("preBuild") { dependsOn(copyChangelog) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.glance.appwidget)
    
    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
