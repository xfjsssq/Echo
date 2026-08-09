plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.echo.recorder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.echo.recorder"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 发布签名配置模板 (E1c): 填入你的 release keystore 信息以启用正式签名.
    // 生成密钥示例: keytool -genkey -v -keystore echo-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias echo
    signingConfigs {
        create("release") {
            storeFile = file(findProperty("ECHO_STORE_FILE") ?: "echo-release.jks")
            storePassword = findProperty("ECHO_STORE_PASSWORD") as String?
            keyAlias = findProperty("ECHO_KEY_ALIAS") as String?
            keyPassword = findProperty("ECHO_KEY_PASSWORD") as String?
        }
    }

    buildTypes {
        debug {
            // debug 使用默认调试签名.
        }
        release {
            isMinifyEnabled = false
            // 启用正式签名: 取消下行注释并在 gradle.properties 提供 ECHO_* 属性.
            // signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Kotlin 1.9.24 不支持 compose 插件, 必须用独立的 compose compiler extension.
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1") // SAF DocumentFile (公共目录备份)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Jetpack Compose (BOM 2024.06.00 对齐 Kotlin 1.9.24 + compose compiler 1.5.14)
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// 辅助 task: 把主代码 + test runtime classpath 写到文件 (供本地测试脚本用)
tasks.register("dumpDebugRuntimeClasspath") {
    doLast {
        val file = rootProject.file("_main_cp.txt")
        val cp = configurations.getByName("debugRuntimeClasspath").joinToString(";") { it.absolutePath }
        file.writeText(cp)
    }
}

tasks.register("dumpDebugUnitTestRuntimeClasspath") {
    doLast {
        val file = rootProject.file("_test_cp_jars.txt")
        val cp = configurations.getByName("debugUnitTestCompileClasspath").joinToString(";") { it.absolutePath }
        file.writeText(cp)
    }
}
