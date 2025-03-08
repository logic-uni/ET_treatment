// 应用插件
plugins {
    id 'com.android.application'  // Android应用插件
    id 'org.jetbrains.kotlin.android' // Kotlin支持
}

android {
    // 编译配置
    compileSdk 34  // 使用Android 14 API编译
    namespace 'com.example.vibrationbracelet' // 应用包名

    defaultConfig {
        applicationId "com.example.vibrationbracelet" // 应用ID
        minSdk 24       // 最低支持Android 7.0
        targetSdk 34    // 目标适配Android 14
        versionCode 1   // 内部版本号
        versionName "1.0.0" // 用户可见版本

        // 测试配置
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    // 构建类型
    buildTypes {
        release {
            minifyEnabled true     // 启用代码混淆
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
        debug {
            debuggable true
        }
    }

    // 编译选项
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
                targetCompatibility JavaVersion.VERSION_17
    }

    // Kotlin选项
    kotlinOptions {
        jvmTarget = '17'
    }

    // 启用ViewBinding/DataBinding
    buildFeatures {
        viewBinding true
    }
}

// 依赖管理
dependencies {
    // ===== 官方库 =====
    implementation 'androidx.core:core-ktx:1.12.0'         // Kotlin扩展
    implementation 'androidx.appcompat:appcompat:1.6.1'    // 兼容支持库
    implementation 'com.google.android.material:material:1.9.0' // Material组件

    // ===== 图表库 =====
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    // ===== 网络通信 =====
    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'  // MQTT核心
    implementation 'org.eclipse.paho:org.eclipse.paho.android.service:1.1.1' // Android服务

    // ===== 测试库 =====
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}