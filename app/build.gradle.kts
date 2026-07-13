import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.hilt.android)
}

apply(plugin = "com.google.gms.google-services")

// Cargar las propiedades de la keystore de release
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.are.distribuidora"
    compileSdk {
        version = release(36)
    }

    buildFeatures {
        viewBinding = true
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile", "release-keystore.jks"))
            storePassword = keystoreProperties.getProperty("storePassword", "")
            keyAlias = keystoreProperties.getProperty("keyAlias", "")
            keyPassword = keystoreProperties.getProperty("keyPassword", "")
        }
    }

    defaultConfig {
        applicationId = "com.are.distribuidora"
        minSdk = 30
        targetSdk = 36
        val appVersionName = "3.0.0"
        versionCode = 30000
        versionName = appVersionName

        // Etiquetas de versión visibles (splash + footer de login) generadas desde
        // versionName para que reflejen la versión real y nunca se desfasen.
        // Reemplazan los <string> hardcodeados que antes decían "v1.0.0".
        resValue("string", "splash_version", "v$appVersionName")
        resValue("string", "login_footer", "DailyStock v$appVersionName · OFFLINE LISTA")

        // Runner para Hilt en instrumented tests
        testInstrumentationRunner = "com.are.distribuidora.HiltTestRunner"
        // Ya no usamos runnerBuilder porque en este setup no existe la clase en el classpath del device.
        testInstrumentationRunnerArguments.remove("runnerBuilder")
    }

    buildTypes {
        release {
            // Activar R8/Proguard en release para reducir tamaño y ofuscar código
            isMinifyEnabled = true
            isShrinkResources = true

            isDebuggable = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Firmar release con keystore de producción
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // UI base para Fragments + ConstraintLayout (Home con bottom nav + fragment container)
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    implementation("com.google.firebase:firebase-firestore-ktx:25.1.0")
    implementation("com.google.firebase:firebase-auth-ktx:23.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")


    implementation("com.google.guava:guava:32.1.3-android")
    // Hilt - DI
    implementation("com.google.dagger:hilt-android:2.51")
    implementation(libs.identity.jvm)
    kapt("com.google.dagger:hilt-android-compiler:2.51")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // DataStore (persistencia local para sesión)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ViewModel + viewModelScope
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    // by viewModels() en ComponentActivity
    implementation("androidx.activity:activity-ktx:1.9.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.6.1")
    kaptTest("androidx.room:room-compiler:2.6.1")

    // Android instrumented tests separados
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    // MockK para Android tests
    androidTestImplementation("io.mockk:mockk-android:1.13.12")

    // Hilt en androidTest
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.51")

    // Glide (carga de imágenes en UI)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Test helpers for coroutines in androidTest
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Paging 3
    implementation("androidx.paging:paging-runtime-ktx:3.3.0")
    implementation("androidx.paging:paging-compose:3.3.0")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-storage-ktx")

    // CameraX (escáner de código de barras)
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Requerido por CameraX (ListenableFuture)
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}
