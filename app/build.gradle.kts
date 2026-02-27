import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tazztone.losslesscut"
    compileSdk = libs.versions.compileSdk.get().toInt()

    // Built-in Kotlin support in AGP 9.0+
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            storeFile = project.findProperty("storeFile")?.toString()?.takeIf { it.isNotEmpty() }?.let { file(it) }
            storePassword = project.findProperty("storePassword")?.toString()
            keyAlias = project.findProperty("keyAlias")?.toString()
            keyPassword = project.findProperty("keyPassword")?.toString()
        }
    }

    defaultConfig {
        applicationId = "com.tazztone.losslesscut"
        minSdk = 26
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = project.findProperty("versionCode")?.toString()?.toInt() ?: Int.MAX_VALUE
        versionName = project.findProperty("versionName")?.toString() ?: "debug-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }



    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.withType<Test>().configureEach {
    maxHeapSize = System.getProperty("test.maxHeapSize", "1024m")
}

dependencies {
    runtimeOnly(project(":engine"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    
    implementation(libs.google.material)
    implementation(libs.lottie)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.serialization.json)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.konsist)
    testImplementation(libs.mockk.android)
    testImplementation(libs.hilt.android.testing)
    debugImplementation(libs.androidx.fragment.testing)
    kspTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.fragment.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
}
