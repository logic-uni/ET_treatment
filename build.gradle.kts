// 顶级构建配置，适用于所有子模块
buildscript {
    // 定义全局依赖仓库
    repositories {
        google()          // Google Maven仓库
        mavenCentral()    // Maven中央仓库
        maven { url 'https://jitpack.io' } // 第三方仓库（如MPAndroidChart）
    }


    // 配置Gradle插件版本
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.0' // Android Gradle插件
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20' // Kotlin插件
    }
}

// 所有子模块共享的配置
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// 清理构建目录的任务
task clean(type: Delete) {
    delete rootProject.buildDir
}