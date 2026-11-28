plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
}

val driverFirebase = mapOf(
    "google_app_id" to "FIREBASE_DRIVER_APP_ID",
    "google_api_key" to "FIREBASE_DRIVER_API_KEY",
    "project_id" to "FIREBASE_DRIVER_PROJECT_ID",
    "gcm_defaultSenderId" to "FIREBASE_DRIVER_SENDER_ID",
).mapValues { (_, name) -> providers.environmentVariable(name).orElse("").get().trim() }

android {
    namespace = "vn.hutech.foodexpress.driver"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.hutech.foodexpress.driver"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "vn.hutech.foodexpress.driver.offer.OfferUiInstrumentation"
        if (driverFirebase.values.all { it.isNotEmpty() }) {
            driverFirebase.forEach { (resource, value) -> resValue("string", resource, value) }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core"))
    implementation(libs.retrofit.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation("com.google.firebase:firebase-messaging:25.0.2")
    testImplementation("junit:junit:4.13.2")
}
