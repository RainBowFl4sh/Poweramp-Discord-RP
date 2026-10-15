import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Default Discord application ID: gradle.properties (discordApplicationId) or -PdiscordApplicationId=...
val discordApplicationId: String =
    (project.findProperty("discordApplicationId") as String?)?.trim().orEmpty()
// Discord application IDs are 17-20 digit snowflakes; anything else would only fail later on the phone
if (discordApplicationId.isNotEmpty() && !Regex("[0-9]{17,20}").matches(discordApplicationId)) {
    throw GradleException("discordApplicationId must be the 17-20 digit Application ID from the Discord Developer Portal, got \"$discordApplicationId\"")
}

android {
    namespace = "io.github.rainbowfl4sh.powerampdiscordrp"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rainbowfl4sh.powerampdiscordrp"
        minSdk = 26
        targetSdk = 36
        versionCode = 10100
        versionName = "1.1.0"
        buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordApplicationId\"")
        buildConfigField("String", "GITHUB_REPO", "\"RainBowFl4sh/Poweramp-Discord-RP\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing from environment variables (CI secrets); without them the release build stays unsigned.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: removes unused code and optimizes the rest (Compose runs much smoother than in a debug build);
            // app/src/main/baseline-prof.txt is compiled in as well (only non-debuggable builds use it)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!keystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
        }
        // Optimized like a release, but signed with the debug key: installs over the debug builds (same application ID,
        // same key) to try how the app really runs. Not built by CI: ./gradlew :app:assembleStaging
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

composeCompiler {
    // No extra Compose group mapping for R8: the minified builds keep their names (proguard-rules.pro), and the mapping
    // task would need one more tool download (compose-group-mapping) for every release build
    includeComposeMappingFile.set(false)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
