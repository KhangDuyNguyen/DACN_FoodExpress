import groovy.json.JsonOutput
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localVietmapTilemapKey = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText.orElse("").map { content ->
        Properties().apply { load(content.reader()) }.getProperty("VIETMAP_TILEMAP_KEY", "").trim()
    }
val environmentVietmapTilemapKey = providers.environmentVariable("VIETMAP_TILEMAP_KEY").map { it.trim() }
val firebaseResources = mapOf(
    "google_app_id" to "FIREBASE_CUSTOMER_APP_ID",
    "google_api_key" to "FIREBASE_CUSTOMER_API_KEY",
    "project_id" to "FIREBASE_CUSTOMER_PROJECT_ID",
    "gcm_defaultSenderId" to "FIREBASE_CUSTOMER_SENDER_ID",
).mapValues { (_, name) -> providers.environmentVariable(name).orElse("").get().trim() }

android {
    namespace = "vn.hutech.foodexpress.customer"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.hutech.foodexpress.customer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "VIETMAP_TILEMAP_KEY", JsonOutput.toJson(environmentVietmapTilemapKey.orElse("").get()))
        if (firebaseResources.values.all { it.isNotEmpty() }) {
            firebaseResources.forEach { (name, value) -> resValue("string", name, value) }
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

    buildTypes {
        debug {
            buildConfigField("String", "VIETMAP_TILEMAP_KEY",
                JsonOutput.toJson(environmentVietmapTilemapKey.orElse(localVietmapTilemapKey).get()))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.hilt.android)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.github.vietmap-company:maps-sdk-android:3.1.0")
    implementation("com.github.vietmap-company:vietmap-services-geojson-android:1.0.0")
    implementation("com.google.firebase:firebase-messaging:25.0.2")
    testImplementation("junit:junit:4.13.2")
}
