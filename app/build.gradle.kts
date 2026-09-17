plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.luna.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.luna.music"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.2"

        ndk {
            // 只保留手机主流 ABI（arm64），避免 FFmpeg 原生库让 APK 翻倍
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePass = providers.environmentVariable("LUNA_KEYSTORE_PASS")
            storeFile = file(System.getProperty("user.home") + "/.luna-keystore/luna-release.keystore")
            storePassword = keystorePass.getOrElse("missing")
            keyAlias = "luna"
            keyPassword = keystorePass.getOrElse("missing")
            // 最大安装兼容性：同时启用 v1(JAR) + v2 签名，
            // 避免部分文件管理器/旧安装通道对纯 v2 签名报“软件包无效”
            isV1SigningEnabled = true
            isV2SigningEnabled = true
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // JavaCPP 的 GraalVM native-image 配置在 Android 上无用，且会在多 ABI 间重复
            excludes += "/META-INF/native-image/**"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // FFmpeg（JavaCV）——用于 WMA / APE 等格式的本机转码
    implementation("org.bytedeco:javacv:1.5.10") {
        exclude(group = "org.bytedeco", module = "opencv")
        exclude(group = "org.bytedeco", module = "openblas")
        exclude(group = "org.bytedeco", module = "flycapture")
        exclude(group = "org.bytedeco", module = "libdc1394")
        exclude(group = "org.bytedeco", module = "libfreenect")
        exclude(group = "org.bytedeco", module = "libfreenect2")
        exclude(group = "org.bytedeco", module = "librealsense")
        exclude(group = "org.bytedeco", module = "librealsense2")
        exclude(group = "org.bytedeco", module = "videoinput")
        exclude(group = "org.bytedeco", module = "artoolkitplus")
        exclude(group = "org.bytedeco", module = "leptonica")
        exclude(group = "org.bytedeco", module = "tesseract")
    }
    implementation("org.bytedeco:ffmpeg:6.1.1-1.5.10")
    runtimeOnly("org.bytedeco:ffmpeg:6.1.1-1.5.10:android-arm64")
    runtimeOnly("org.bytedeco:javacpp:1.5.10:android-arm64")
}
