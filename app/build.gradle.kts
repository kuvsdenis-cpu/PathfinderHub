import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("kotlin-parcelize")
}

// ============================================================
// Загрузка local.properties (Yandex Object Storage credentials)
// ============================================================
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.pathfinder.hub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pathfinder.hub"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // ============================================================
        // Yandex Object Storage — читаем из local.properties
        // ============================================================
        buildConfigField(
            "String",
            "YANDEX_ACCESS_KEY_ID",
            "\"${localProperties.getProperty("YANDEX_ACCESS_KEY_ID", "")}\""
        )
        buildConfigField(
            "String",
            "YANDEX_SECRET_ACCESS_KEY",
            "\"${localProperties.getProperty("YANDEX_SECRET_ACCESS_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "YANDEX_BUCKET_NAME",
            "\"${localProperties.getProperty("YANDEX_BUCKET_NAME", "pathfinder-hub-media")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    // ✅ Для AGP 8.x правильный способ — kotlinOptions
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            pickFirsts.add("META-INF/services/javax.xml.stream.XMLInputFactory")
        }
    }
}

dependencies {
    // Yandex Object Storage (S3-совместимое API)
    implementation("software.amazon.awssdk:s3:2.29.0")
    implementation("software.amazon.awssdk:url-connection-client:2.29.0")

    // ============ StAX: интерфейс + реализация (для XML-ответов S3) ============
    implementation("javax.xml.stream:stax-api:1.0-2")
    implementation("com.fasterxml:aalto-xml:1.3.3")

    // Desugaring для java.time / java.util.stream (AWS SDK v2)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // ============ Версии ============
    val roomVersion = "2.8.4"
    val hiltVersion = "2.58"
    val androidxHiltVersion = "1.3.0"
    val coroutinesVersion = "1.10.2"
    val gsonVersion = "2.13.1"
    val lifecycleVersion = "2.9.3"
    val navigationVersion = "2.9.6"
    val workManagerVersion = "2.11.0"
    val firebaseBomVersion = "34.6.0"
    val composeBomVersion = "2025.09.00"
    val coreKtxVersion = "1.17.0"
    val activityComposeVersion = "1.10.1"
    val junitVersion = "4.13.2"
    val androidxJunitVersion = "1.3.0"
    val espressoVersion = "3.7.0"

    // ============ AndroidX Core ============
    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.activity:activity-compose:$activityComposeVersion")

    // ============ Compose ============
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:$navigationVersion")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ============ Coil (для будущих превью картинок в чате) ============
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ============ Room ============
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ============ Hilt ============
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-compiler:$hiltVersion")

    // ============ Hilt (AndroidX) ============
    implementation("androidx.hilt:hilt-navigation-compose:$androidxHiltVersion")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:$androidxHiltVersion")
    implementation("androidx.hilt:hilt-work:$androidxHiltVersion")
    ksp("androidx.hilt:hilt-compiler:$androidxHiltVersion")

    // ============ Coroutines ============
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    // ============ Gson ============
    implementation("com.google.code.gson:gson:$gsonVersion")

    // ============ Firebase ============
    implementation(platform("com.google.firebase:firebase-bom:$firebaseBomVersion"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // firebase-storage убран — используем Yandex Object Storage
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    // ============ WorkManager ============
    implementation("androidx.work:work-runtime-ktx:$workManagerVersion")

    // ============ PDF Viewer ============
    implementation("io.github.oothp:android-pdf-viewer:3.2.0-beta06")

    // ============ Tests ============
    testImplementation("junit:junit:$junitVersion")
    androidTestImplementation("androidx.test.ext:junit:$androidxJunitVersion")
    androidTestImplementation("androidx.test.espresso:espresso-core:$espressoVersion")
    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}