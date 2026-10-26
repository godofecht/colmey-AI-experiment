plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.colmey.instrument"
    compileSdk = 35
    defaultConfig {
        applicationId = "ai.colmey.instrument"
        minSdk = 24
        targetSdk = 35
        versionCode = providers.environmentVariable("COLMEY_BUILD_NUMBER").orNull?.toIntOrNull() ?: 1
        versionName = "0.1.${versionCode}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("preview") {
            val path = providers.environmentVariable("COLMEY_KEYSTORE_PATH").orNull
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = providers.environmentVariable("COLMEY_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("COLMEY_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("COLMEY_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (!providers.environmentVariable("COLMEY_KEYSTORE_PATH").orNull.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("preview")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("androidx.core:core:1.13.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
