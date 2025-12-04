# Lumina Virtual Studio

<p align="center">
  <img src="https://img.shields.io/badge/Android-10%2B-green?logo=android" alt="Android 10+">
  <img src="https://img.shields.io/badge/Kotlin-1.9.20-purple?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/C%2B%2B-NDK-blue?logo=cplusplus" alt="C++ NDK">
  <img src="https://img.shields.io/badge/Python-3.11-yellow?logo=python" alt="Python 3.11">
</p>

A high-performance hybrid Android application combining **Kotlin UI**, **C++ rendering engine**, and **Python AI orchestration** for real-time creative media processing.

---

## ✨ Features

- **Glassmorphic UI** — Modern Material 3 design with blur effects and translucent surfaces
- **Real-Time Rendering** — GPU-accelerated processing via Vulkan/OpenGL with std140 memory alignment
- **AI-Powered Intent Parsing** — Natural language commands processed by Qwen 3 model
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
│   Vulkan/OpenGL      │       Qwen 3 + Micro-DAG             │
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

## 🛠️ Tech Stack

- **UI**: Jetpack Compose, Material 3
- **Native**: C++17, Vulkan, OpenGL ES 3.2, Oboe
- **AI**: Python 3.11 (Chaquopy), Qwen 3
- **Build**: Gradle Kotlin DSL, CMake
- **Testing**: JUnit, MockK, pytest, Kover

---

<p align="center">
  <b>Lumina Virtual Studio</b> — Where AI meets real-time rendering
</p>
