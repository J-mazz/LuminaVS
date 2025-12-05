# Lumina Virtual Studio — AI coding guide

**Architecture map**
- Kotlin UI (Jetpack Compose + Material3) drives state in `LuminaCore.kt` (ViewModel, `LuminaState`, intents) and wires bridges.
- Native bridge: `NativeEngine.kt` → JNI (`native-lib.cpp`) → `engine_structs.h` state. Python bridge: `PythonBridge.kt` → Chaquopy → `app/src/main/python/orchestrator.py` (Qwen 3 Micro-DAG for intent parsing).
- Rendering: C++ currently stubs GPU init; `LuminaEngineCore` manages state, surface, frame timing. Camera: CameraX via `CameraController.kt` + `CameraPreviewArea` in `MainActivity.kt`.

**Key files**
- UI: `MainActivity.kt` (permissions, camera UI), `LuminaCore.kt` (state, ViewModel), `ui/theme/*`.
- Camera: `CameraController.kt`, `CameraPreviewArea` composable (preview, photo, video, zoom, tap-to-focus, switch cams).
- Model download: `ModelDownloader.kt` (`qwen3-1.7b-q4_k_m.gguf`, no checksum enforced). Python orchestrator expects same filename.
- Native: `app/src/main/cpp/native-lib.cpp`, `engine_structs.h`. JSON update is stub; EGL/Vulkan stubs remain. Asset manager global ref now released; ANativeWindow refcount fixed.
- Python: `app/src/main/python/orchestrator.py` (intent parsing, rule/LLM fallback). Ensure assets path points to downloaded model.

**Build & test**
- Android build: `./gradlew assembleDebug` (requires `local.properties` with `sdk.dir` or ANDROID_HOME/ANDROID_SDK_ROOT; NDK via Gradle). Native builds via externalNativeBuild CMake.
- JVM tests: `./gradlew test`.
- Python tests (orchestrator): `python -m pytest app/src/test/python/test_orchestrator.py` (outside Android build; ensure python deps installed to match Chaquopy, e.g., numpy/requests).

**Permissions & camera**
- Runtime permissions handled in `MainActivity` using ActivityResultContracts; modern `READ_MEDIA_*` for API 33+, legacy external storage maxSdk=32.
- Camera preview expects permissions granted before start; CameraController binds Preview, ImageCapture, VideoCapture to lifecycle. Use `CameraPreviewArea` pattern for zoom/tap focus, photo/video actions, camera switch.

**State & serialization**
- `LuminaState.toJson/fromJson` uses shared Gson instance; ViewModel increments `stateId` on every transform. Native `updateStateFromJson` currently stub: integrate proper parsing and apply to `LuminaState` analog.
- Render modes/effects defined in `engine_structs.h` & Kotlin enums; keep consistency across Kotlin/Python/C++.

**Models & assets**
- Model filename constant: `qwen3-1.7b-q4_k_m.gguf`. Download path: `filesDir/models/`. Python orchestrator uses same; grammar file `qwen_grammar.gbnf` in assets.

**Native lifecycle**
- JNI shutdown releases asset manager global ref and native window; ensure to acquire/release windows via `NativeEngine.setSurface` and call shutdown in Activity onDestroy. Add pause/resume hooks when integrating rendering.

**Patterns & conventions**
- Compose: hoist state via ViewModel; side-effects in `LaunchedEffect(Unit)` for bridge injection. Use Material3 Glassmorphic containers defined in `LuminaCore` helpers.
- Coroutines: heavy work on `Dispatchers.IO` (orchestrator call already switched). Avoid blocking Main.
- Keep filenames/constants in sync across Kotlin/Python/C++ to avoid runtime mismatches.

**Missing/placeholder work (do not add new stubs)**
- GPU pipeline (EGL/Vulkan), shader effects, JSON -> native state parsing, MediaStore save UX, DI (Hilt), crash reporting, accessibility, onboarding/error states still need full implementations. Prefer real code over placeholder logs.

**Gotchas**
- Chaquopy Python version pinned to 3.11 in `app/build.gradle.kts`; limited wheels. If issues arise, consider 3.8 per Chaquopy warning.
- Model download currently lacks checksum enforcement (intentional). If adding, ensure streaming hash and strict compare.
- Gradle uses Kotlin DSL; compose BOM pinned; Kover enabled; externalNativeBuild cmake version 3.22.1.

Use these conventions to stay aligned and productive.
