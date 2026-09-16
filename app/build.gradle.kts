import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val stableStoreFile = providers.environmentVariable("CAMPUS_SIGNING_STORE_FILE").orNull
val stableStorePassword = providers.environmentVariable("CAMPUS_SIGNING_STORE_PASSWORD").orNull
val stableKeyAlias = providers.environmentVariable("CAMPUS_SIGNING_KEY_ALIAS").orNull
val stableKeyPassword = providers.environmentVariable("CAMPUS_SIGNING_KEY_PASSWORD").orNull
val hasStableSigning = listOf(
    stableStoreFile,
    stableStorePassword,
    stableKeyAlias,
    stableKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.hustar0311.campusdirect"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.hustar0311.campusdirect"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasStableSigning) {
            create("campusStable") {
                storeFile = file(requireNotNull(stableStoreFile))
                storePassword = requireNotNull(stableStorePassword)
                keyAlias = requireNotNull(stableKeyAlias)
                keyPassword = requireNotNull(stableKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("campusStable")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("campusStable")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging.resources.excludes += setOf(
        "META-INF/DEPENDENCIES",
        "META-INF/LICENSE*",
        "META-INF/NOTICE*",
    )
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsch)
    implementation(libs.bouncycastle)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
