plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("com.chaquo.python")
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
}

android {
    namespace = "com.lumina.engine"
    compileSdk = 34

    ndkPath = "/home/joseph-mazzini/LuminaVS/Android/Sdk/ndk/27.0.12077973"

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    defaultConfig {
        applicationId = "com.lumina.engine"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // NDK Configuration
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_TOOLCHAIN=clang"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        prefab = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.11"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

chaquopy {
    defaultConfig {
        // Target Python 3.13 runtime (supported by Chaquopy 17.0)
        version = "3.13"
        // Build wheels with the local 3.13 venv for consistency
        buildPython = listOf("${rootDir}/.venv313/bin/python")
        pyc { src = false }
        pip {
            // Only required deps; numpy omitted to avoid native build overhead
            install("requests==2.31.0")
            install("legacy-cgi")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")

    // Native Libraries (Prefab)
    implementation("com.google.oboe:oboe:1.7.0")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.camera:camera-video:1.3.1")

    // JSON Serialization
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Settings persistence
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("app.cash.turbine:turbine:0.13.0")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation("org.robolectric:robolectric:4.10.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // CameraX testing utilities (FakeImageProxy)
    androidTestImplementation("androidx.camera:camera-testing:1.3.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

koverReport {
    defaults {
        html {
            onCheck = true
        }
        xml {
            onCheck = true
        }
    }
    filters {
        excludes {
            classes(
                "*Activity",
                "*Application",
                "*.BuildConfig",
                "*.ui.theme.*",
                "*ComposableSingletons*",
                "*_Factory*",
                "*_HiltModules*"
            )
        }
    }
}

// Use fully qualified types in Gradle script to avoid top-level imports

// Compile GLSL shaders to SPIR-V and generate a C++ header with arrays used by renderer_vulkan
val generateVulkanShaders = tasks.register("generateVulkanShaders") {
    doLast {
        val ndk = android.ndkPath ?: System.getenv("ANDROID_NDK")
        if (ndk == null) {
            throw GradleException("NDK path not defined; set android.ndkPath or ANDROID_NDK env var")
        }
        val glslc = file("$ndk/shader-tools/linux-x86_64/glslc").absolutePath
        val shaderDir = file("src/main/assets/shaders/vulkan")
        val outDir = file("src/main/cpp/generated")
        outDir.mkdirs()

        val vertIn = file(shaderDir.toString() + "/passthrough.vert")
        val fragIn = file(shaderDir.toString() + "/chromatic_aberration.frag")
        val vertSpv = file(outDir.toString() + "/passthrough.vert.spv")
        val fragSpv = file(outDir.toString() + "/chromatic_aberration.frag.spv")

        project.exec { commandLine = listOf(glslc, vertIn.absolutePath, "-o", vertSpv.absolutePath) }
        project.exec { commandLine = listOf(glslc, fragIn.absolutePath, "-o", fragSpv.absolutePath) }

        fun writeArray(name: String, spv: File): String {
            val bytes = spv.readBytes()
            val size = bytes.size / 4
            val sb = StringBuilder()
            sb.append("const std::vector<uint32_t> $name = {")
            for (i in 0 until size) {
                val idx = i * 4
                val v = (bytes[idx].toInt() and 0xFF) or
                        ((bytes[idx + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[idx + 2].toInt() and 0xFF) shl 16) or
                        ((bytes[idx + 3].toInt() and 0xFF) shl 24)
                sb.append(v.toLong() and 0xFFFFFFFFL)
                if (i < size - 1) sb.append(", ")
            }
            sb.append("};\n")
            return sb.toString()
        }

        val header = File(outDir, "shaders_generated.h")
        header.writeText("#pragma once\n#include <array>\n#include <cstdint>\n\n")
        header.appendText(writeArray("VulkanRenderer::kVertSpv", vertSpv))
        header.appendText(writeArray("VulkanRenderer::kFragSpv", fragSpv))
    }
}

// Ensure shader generation runs before native build (externalNativeBuild tasks)
tasks.whenTaskAdded {
    if (name.startsWith("externalNativeBuild")) {
        dependsOn(generateVulkanShaders)
    }
}

// Model is downloaded at runtime, not at build time
// See ModelDownloader.kt for runtime download implementation
