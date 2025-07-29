plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Serialization
    kotlin("plugin.serialization") version "2.1.20"

    // Hilt
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")

    alias(libs.plugins.google.gms.google.services)

    // Add the Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics")

    // Google Maps
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.temuin.temuin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.temuin.temuin"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Enable Crashlytics for release builds
            firebaseCrashlytics {
                mappingFileUploadEnabled = true
            }
            buildConfigField("String", "RECAPTCHA_SITE_KEY", "\"6LckElgrAAAAAMnI-dhYinNlUcNZJeaYEjyxsbVc\"")
        }
        debug {
            // Enable Crashlytics for debug builds
            firebaseCrashlytics {
                mappingFileUploadEnabled = false
            }
            buildConfigField("String", "RECAPTCHA_SITE_KEY", "\"6LckElgrAAAAAMnI-dhYinNlUcNZJeaYEjyxsbVc\"")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(libs.places)
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Add core library desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(libs.google.maps)

    // Google Maps Compose and Places API
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.android.libraries.places:places:3.3.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    val composeBomVersion = "2024.02.00"
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.google.android.recaptcha:recaptcha:18.7.1")

    // Add Material Design Components for proper corner attributes
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Firebase - using BOM to manage versions
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    // Add the dependencies for the Crashlytics and Analytics libraries
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    // Firebase function dependencies
    implementation("com.google.firebase:firebase-functions-ktx")

    // Calendar - update to latest version that's compatible with your Compose version
    implementation("com.kizitonwose.calendar:compose:2.5.1")

    // Jetpack Compose integration with fixed version
    implementation("androidx.navigation:navigation-compose:2.7.7")

    //Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.2")
    kapt("com.google.dagger:hilt-android-compiler:2.56.2")
    //Hilt view model
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
}