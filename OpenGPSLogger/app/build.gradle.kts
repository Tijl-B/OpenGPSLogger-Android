plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "eu.tijlb.opengpslogger"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.tijlb.opengpslogger"
        minSdk = 34
        targetSdk = 35
        versionCode = 45
        versionName = "1.2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    flavorDimensions += "version"

    productFlavors {
        create("default") {
            dimension = "version"
        }
        create("dev") {
            dimension = "version"
            applicationId = "eu.tijlb.opengpslogger.dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Dev OpenGPSLogger")
        }
    }
}

dependencies {
    implementation(libs.openlocationcode)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.play.services.location)
    implementation(libs.androidx.ui.desktop)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.glide)
    implementation(libs.core.ktx)

    annotationProcessor(libs.compiler)
    
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}