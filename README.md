# Lumina Virtual Studio

<p align="center">
  <img src="https://img.shields.io/badge/Android-29%2B-green?logo=android" alt="Android 29+">
  <img src="https://img.shields.io/badge/Kotlin-1.9.20-purple?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/C%2B%2B-NDK-blue?logo=cplusplus" alt="C++ NDK">
  <img src="https://img.shields.io/badge/Python-3.11-yellow?logo=python" alt="Python 3.11">
  <img src="https://img.shields.io/badge/Coverage-79%25-brightgreen" alt="Coverage">
</p>

A high-performance hybrid Android application combining **Kotlin UI**, **C++ rendering engine**, and **Python AI orchestration** for real-time creative media processing.

---

## ✨ Features

- **Glassmorphic UI** — Modern Material 3 design with blur effects and translucent surfaces
- **Real-Time Rendering** — GPU-accelerated processing via Vulkan/OpenGL with std140 memory alignment
- **AI-Powered Intent Parsing** — Natural language commands processed by Qwen 2.5 1.5B model
- **Multi-Mode Rendering** — Photo, Video, Portrait, Night, Cinematic, and RAW modes
- **Live Effects Pipeline** — Bloom, vignette, color grading, film grain, and chromatic aberration

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Kotlin UI Layer                          │
│              Jetpack Compose + Material 3                   │
├─────────────────────────────────────────────────────────────┤
│                   JNI Bridge Layer                          │
│            NativeEngine ↔ PythonBridge                      │
├──────────────────────┬──────────────────────────────────────┤
│   C++ Rendering      │       Python AI Orchestrator         │
│   Vulkan/OpenGL      │       Qwen 2.5 + Micro-DAG          │
│   Oboe Audio         │       Intent Classification          │
└──────────────────────┴──────────────────────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 35
- NDK 25.2.9519653
- CMake 3.22.1

### Build

```bash
git clone https://github.com/J-mazz/LuminaVS.git
cd LuminaVS
./gradlew assembleDebug
```

### Run Tests

```bash
# Python tests
python3 -m pytest app/src/test/python/ -v --cov=app/src/main/python

# Kotlin tests
./gradlew test koverHtmlReport
```

---

## 📁 Project Structure

```
LuminaVS/
├── app/
│   ├── src/main/
│   │   ├── java/com/lumina/engine/   # Kotlin source
│   │   │   ├── LuminaCore.kt         # Data models & ViewModel
│   │   │   ├── MainActivity.kt       # Entry point
│   │   │   ├── NativeEngine.kt       # JNI wrapper
│   │   │   └── PythonBridge.kt       # Chaquopy bridge
│   │   ├── cpp/                      # Native layer
│   │   │   ├── engine_structs.h      # Shared memory schema
│   │   │   └── native-lib.cpp        # Rendering engine
│   │   └── python/                   # AI layer
│   │       └── orchestrator.py       # Intent classification
│   └── src/test/                     # Test suites
└── coverage_reports/                 # Test coverage
```

---

## 🎯 Render Modes

| Mode | Description |
|------|-------------|
| `PHOTO` | Standard capture with auto-exposure |
| `VIDEO` | Continuous recording with stabilization |
| `PORTRAIT` | Depth-aware bokeh processing |
| `NIGHT` | Low-light enhancement |
| `CINEMATIC` | 24fps with film color science |
| `RAW` | Unprocessed sensor output |

---

## 🤖 AI Commands

The orchestrator understands natural language:

```
"Make it warmer"           → Adjusts color temperature
"Add cinematic look"       → Applies film grain + vignette
"Enhance the shadows"      → Boosts shadow detail
"Portrait mode at 50%"     → Sets bokeh intensity
```

---

## 📊 Test Coverage

| Component | Coverage |
|-----------|----------|
| Python Orchestrator | **79%** |
| Kotlin Unit Tests | 90+ tests |

---

## 🛠️ Tech Stack

- **UI**: Jetpack Compose, Material 3
- **Native**: C++17, Vulkan, OpenGL ES 3.2, Oboe
- **AI**: Python 3.11 (Chaquopy), Qwen 2.5 1.5B
- **Build**: Gradle Kotlin DSL, CMake
- **Testing**: JUnit, MockK, pytest, Kover

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

<p align="center">
  <b>Lumina Virtual Studio</b> — Where AI meets real-time rendering
</p>

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer (Kotlin)                        │
│            Jetpack Compose + Glassmorphic Design            │
├─────────────────────────────────────────────────────────────┤
│                  Logic Layer (Python)                       │
│         Qwen 2.5 1.5B for Semantic Intent Parsing           │
├─────────────────────────────────────────────────────────────┤
│                 Render Layer (C++)                          │
│         Vulkan/OpenGL Real-time Video Processing            │
└─────────────────────────────────────────────────────────────┘
```

## Features

- **Natural Language Control**: Describe effects like "make it dreamy" or "add subtle blur"
- **Real-time Rendering**: GPU-accelerated video processing via Vulkan/OpenGL
- **Multiple Render Modes**: Passthrough, Stylized, Segmented, Depth Map, Normal Map
- **Visual Effects**: Blur, Bloom, Color Grade, Vignette, Chromatic Aberration, Film Grain, Sharpen
- **Glassmorphic UI**: Modern Material 3 design with blur and transparency effects

## Project Structure

```
app/src/main/
├── java/com/lumina/engine/
│   ├── LuminaCore.kt         # Data contracts & UI components
│   ├── MainActivity.kt       # Entry point
│   ├── LuminaApplication.kt  # Application class
│   ├── NativeEngine.kt       # JNI wrapper
│   ├── PythonBridge.kt       # Chaquopy wrapper
│   └── ui/theme/             # Material 3 theming
├── cpp/
│   ├── CMakeLists.txt        # NDK build config
│   ├── engine_structs.h      # Shared memory schema (std140)
│   └── native-lib.cpp        # JNI bridge implementation
├── python/
│   └── orchestrator.py       # AI logic director
└── assets/
    ├── lumina_app_logic.json # App configuration
    ├── qwen_grammar.gbnf     # LLM output grammar
    └── qwen-2.5-1.5b-instruct-q4_k_m.gguf # Model (add manually)
```

## Build Requirements

- Android Studio Hedgehog or newer
- Android SDK 35
- NDK r25+
- CMake 3.22.1+
- Python 3.11 (via Chaquopy)

## Setup

1. Clone the repository
2. Download the Qwen model file and place in `app/src/main/assets/`
3. Open in Android Studio
4. Sync Gradle
5. Build and run on device (API 29+)

## Model Setup

The app uses Qwen 2.5 1.5B Instruct (Q4_K_M quantized) for intent parsing. 
Download the `.gguf` model file and place it in:
```
app/src/main/assets/qwen-2.5-1.5b-instruct-q4_k_m.gguf
```

**Note**: The model file is large (~1GB) and should not be committed to version control.

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

## License

Proprietary - All rights reserved
