import java.io.FileInputStream
import java.util.Properties
import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin.android)
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.protobuf") version "0.9.4"
    id("kotlin-parcelize")
}


val properties = Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (!localPropertiesFile.exists()) {
    println("local.properties file not found at: ${localPropertiesFile.absolutePath}")
} else {
    try {
        properties.load(FileInputStream(localPropertiesFile))
    } catch(e: Exception){
        println("Error reading local.properties file: ${e.message}")
    }
}

android {
    namespace = "com.example.tlfinal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tlfinal"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val geminiApiKey = properties.getProperty("gemini_api_key") ?: ""
        if (geminiApiKey.isEmpty()) {
            println("gemini_api_key not found in local.properties.")
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xextended-compiler-checks")
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

}

dependencies {
    implementation(libs.play.services.base)
    // AndroidX and Material Design dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview)


    // Firebase dependencies
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage.ktx)
    implementation(platform(libs.firebase.bom.v3280))
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.functions.ktx)

    // Other libraries
    implementation(libs.mhiew.material.calendarview)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx.v190)
    implementation(libs.text.recognition)
    implementation(libs.material.v1110)


    // Glide for Image Loading
    implementation(libs.glide)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.messaging)
    annotationProcessor(libs.compiler)

    // ML Kit Face Detection
    implementation(libs.face.detection)

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}
configurations.all {
    resolutionStrategy {

        force("com.google.protobuf:protobuf-java:3.21.5")// Force a single version of Protobuf
        force("com.google.api.grpc:proto-google-common-protos:2.8.3")
        force("com.google.firebase:protolite-well-known-types:18.0.0")
    }
}