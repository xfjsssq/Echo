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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("app.cash.turbine:turbine:1.1.0")
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
