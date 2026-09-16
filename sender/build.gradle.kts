plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 고정 서명키: 있으면 debug/release 모두 이 키로 서명해 덮어쓰기 업데이트가 가능해진다.
// 비밀번호는 저장소에 두지 않고 환경변수(=GitHub Actions 시크릿)에서만 읽는다.
val keystoreFile = rootProject.file("keystore/gpsbridge.jks")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val useSharedSigning = keystoreFile.exists() && !keystorePassword.isNullOrBlank()
val ciVersionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
    namespace = "com.gpsbridge.sender"
    compileSdk = 34

    signingConfigs {
        if (useSharedSigning) {
            create("shared") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "gpsbridge"
                keyPassword = keystorePassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.gpsbridge.sender"
        minSdk = 24
        targetSdk = 34
        versionCode = ciVersionCode
        versionName = "1.0.$ciVersionCode"
    }

    buildTypes {
        getByName("debug") {
            if (useSharedSigning) {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (useSharedSigning) {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
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
}

dependencies {
    implementation(project(":common"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
