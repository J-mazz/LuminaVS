plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
    `maven-publish`
}

android {
    namespace = "com.lumina.engine"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumina.engine"
        minSdk = 29
        targetSdk = 35
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

        // Chaquopy Python Configuration
        python {
            pip {
                install("numpy")
                install("requests")
                install("mediapipe")
            }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        prefab = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
        version = "3.11"
        pyc {
            src = false
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    // Native Libraries (Prefab)
    implementation("com.google.oboe:oboe:1.7.0")

    // JSON Serialization
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
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

// Download Qwen model at build time
tasks.register("downloadModel") {
    val modelDir = file("src/main/assets/models")
    val modelFile = file("$modelDir/qwen3-1.7b-q4_k_m.gguf")

    outputs.file(modelFile)

    doLast {
        if (!modelFile.exists()) {
            modelDir.mkdirs()
            println("Downloading Qwen 3 1.7B model...")
            val url = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"
            ant.withGroovyBuilder {
                "get"("src" to url, "dest" to modelFile, "verbose" to true)
            }
            println("Model downloaded to: ${modelFile.absolutePath}")
        } else {
            println("Model already exists: ${modelFile.absolutePath}")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadModel")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.lumina"
            artifactId = "engine"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Lumina Virtual Studio")
                description.set("AI-powered camera app with real-time rendering")
                url.set("https://github.com/J-mazz/LuminaVS")
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/J-mazz/LuminaVS")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
