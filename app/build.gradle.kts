import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localSigning =
    Properties().apply {
        val propertiesFile = rootProject.file("keystore.properties")
        if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
    }

android {
    namespace = "app.mizan"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.mizan"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = providers.environmentVariable("MIZAN_KEYSTORE_PATH").orNull ?: localSigning.getProperty("storeFile")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.environmentVariable("MIZAN_KEYSTORE_PASSWORD").orNull ?: localSigning.getProperty("storePassword")
                keyAlias = providers.environmentVariable("MIZAN_KEY_ALIAS").orNull ?: localSigning.getProperty("keyAlias")
                keyPassword = providers.environmentVariable("MIZAN_KEY_PASSWORD").orNull ?: localSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        getByName("androidTest").assets.srcDir("schemas")
        getByName("androidTest").assets.srcDir("../domain/src/test/resources")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.okhttp.logging)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
}
