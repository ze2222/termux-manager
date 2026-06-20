plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // alias(libs.plugins.ksp)   // 在 M5 接入 Room 时启用
}

android {
    namespace = "com.termux.manager"
    compileSdk = 35   // 可按需升至 36(Android 16);35 已覆盖全部 SAF API 且 CI 最稳

    defaultConfig {
        applicationId = "com.termux.manager"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // M1:浏览与异步
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // 后续里程碑按需启用(版本已在 gradle/libs.versions.toml 中备好):
    // implementation(libs.androidx.documentfile)
    // implementation(libs.androidx.datastore.preferences)
    // implementation(libs.androidx.security.crypto)
    // implementation(libs.androidx.room.runtime)
    // implementation(libs.androidx.room.ktx)
    // ksp(libs.androidx.room.compiler)
    // implementation(libs.sshj)
    // implementation(libs.bouncycastle.bcprov)
}
