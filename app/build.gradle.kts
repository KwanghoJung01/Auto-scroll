plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.jace.autoscroll"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jace.autoscroll"
        minSdk = 26
        targetSdk = 35
        // CI 에서 빌드 번호를 넘겨준다. 없으면 로컬 빌드로 본다.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0-local"
    }

    signingConfigs {
        // 저장소에 함께 넣어둔 고정 키. 이게 없으면 빌드마다 임시 debug 키가
        // 새로 만들어져서, 이미 깔린 앱 위에 덮어 설치가 되지 않는다.
        create("shared") {
            storeFile = rootProject.file("keystore/autoscroll.jks")
            storePassword = "autoscroll"
            keyAlias = "autoscroll"
            keyPassword = "autoscroll"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
