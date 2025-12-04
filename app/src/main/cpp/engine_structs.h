#ifndef LUMINA_ENGINE_STRUCTS_H
#define LUMINA_ENGINE_STRUCTS_H

#include <cstdint>
#include <cstring>
#include <string>
#include <array>
#include <vector>

/**
 * Lumina Virtual Studio - Shared Memory Schema
 * 
 * All structs use std140 layout for Vulkan/OpenGL uniform buffer compatibility.
 * 16-byte alignment is enforced for GPU memory access patterns.
 */

namespace lumina {

// Alignment macro for std140 layout compatibility
#define LUMINA_ALIGN(x) alignas(x)

/**
 * Color representation with HDR support
 * std140: vec4 requires 16-byte alignment
 */
struct LUMINA_ALIGN(16) ColorRGBA {
    float r;
    float g;
    float b;
    float a;

    ColorRGBA() : r(0.0f), g(0.0f), b(0.0f), a(1.0f) {}
    ColorRGBA(float r, float g, float b, float a = 1.0f) 
        : r(r), g(g), b(b), a(a) {}

    static ColorRGBA fromHex(uint32_t hex) {
        return ColorRGBA(
            ((hex >> 24) & 0xFF) / 255.0f,
            ((hex >> 16) & 0xFF) / 255.0f,
            ((hex >> 8) & 0xFF) / 255.0f,
            (hex & 0xFF) / 255.0f
        );
    }
};

/**
 * 2D Vector for positions and dimensions
 * std140: vec2 requires 8-byte alignment, padded to 16 for arrays
 */
struct LUMINA_ALIGN(16) Vec2 {
    float x;
    float y;
    float _padding[2]; // Explicit padding for std140 array compatibility

    Vec2() : x(0.0f), y(0.0f), _padding{0.0f, 0.0f} {}
    Vec2(float x, float y) : x(x), y(y), _padding{0.0f, 0.0f} {}
};

/**
 * 3D Vector for spatial coordinates
 * std140: vec3 requires 16-byte alignment
 */
struct LUMINA_ALIGN(16) Vec3 {
    float x;
    float y;
    float z;
    float _padding; // Explicit padding for std140

    Vec3() : x(0.0f), y(0.0f), z(0.0f), _padding(0.0f) {}
    Vec3(float x, float y, float z) : x(x), y(y), z(z), _padding(0.0f) {}
};

/**
 * 4x4 Transform Matrix
 * std140: mat4 requires 16-byte alignment per column
 */
struct LUMINA_ALIGN(16) Mat4 {
    float data[16];

    Mat4() {
        std::memset(data, 0, sizeof(data));
        // Identity matrix
        data[0] = data[5] = data[10] = data[15] = 1.0f;
    }

    static Mat4 identity() {
        return Mat4();
    }

    float* operator[](int col) { return &data[col * 4]; }
    const float* operator[](int col) const { return &data[col * 4]; }
};

/**
 * Render mode enumeration
 */
enum class RenderMode : uint32_t {
    PASSTHROUGH = 0,
    STYLIZED = 1,
    SEGMENTED = 2,
    DEPTH_MAP = 3,
    NORMAL_MAP = 4
};

/**
 * Effect type enumeration
 */
enum class EffectType : uint32_t {
    NONE = 0,
    BLUR = 1,
    BLOOM = 2,
    COLOR_GRADE = 3,
    VIGNETTE = 4,
    CHROMATIC_ABERRATION = 5,
    NOISE = 6,
    SHARPEN = 7
};

/**
 * Processing state enumeration
 */
enum class ProcessingState : uint32_t {
    IDLE = 0,
    PROCESSING = 1,
    RENDERING = 2,
    ERROR = 3
};

/**
 * Effect parameters structure
 * std140: Uniform block with proper alignment
 */
struct LUMINA_ALIGN(16) EffectParams {
    EffectType type;
    float intensity;
    float param1;
    float param2;
    
    ColorRGBA tintColor;
    
    Vec2 center;
    Vec2 scale;

    EffectParams() 
        : type(EffectType::NONE)
        , intensity(1.0f)
        , param1(0.0f)
        , param2(0.0f)
        , tintColor()
        , center(0.5f, 0.5f)
        , scale(1.0f, 1.0f) {}
};

/**
 * Camera state for viewport configuration
 */
struct LUMINA_ALIGN(16) CameraState {
    Mat4 viewMatrix;
    Mat4 projectionMatrix;
    Vec3 position;
    Vec3 lookAt;
    float fov;
    float nearPlane;
    float farPlane;
    float _padding;

    CameraState()
        : viewMatrix()
        , projectionMatrix()
        , position(0.0f, 0.0f, 5.0f)
        , lookAt(0.0f, 0.0f, 0.0f)
        , fov(60.0f)
        , nearPlane(0.1f)
        , farPlane(1000.0f)
        , _padding(0.0f) {}
};

/**
 * Glassmorphic UI parameters
 * Used for the Material 3 design system
 */
struct LUMINA_ALIGN(16) GlassmorphicParams {
    ColorRGBA backgroundColor;
    ColorRGBA borderColor;
    float blurRadius;
    float transparency;
    float borderWidth;
    float cornerRadius;
    
    float saturation;
    float brightness;
    float _padding[2];

    GlassmorphicParams()
        : backgroundColor(1.0f, 1.0f, 1.0f, 0.1f)
        , borderColor(1.0f, 1.0f, 1.0f, 0.2f)
        , blurRadius(20.0f)
        , transparency(0.7f)
        , borderWidth(1.0f)
        , cornerRadius(16.0f)
        , saturation(1.2f)
        , brightness(1.1f)
        , _padding{0.0f, 0.0f} {}
};

/**
 * AI Intent result from Python orchestrator
 */
struct LUMINA_ALIGN(16) AIIntent {
    static constexpr size_t MAX_ACTION_LENGTH = 64;
    static constexpr size_t MAX_TARGET_LENGTH = 128;
    static constexpr size_t MAX_PARAMS_LENGTH = 512;

    char action[MAX_ACTION_LENGTH];
    char target[MAX_TARGET_LENGTH];
    char parameters[MAX_PARAMS_LENGTH];
    float confidence;
    uint32_t timestamp;
    uint32_t _padding[2];

    AIIntent() : confidence(0.0f), timestamp(0) {
        std::memset(action, 0, MAX_ACTION_LENGTH);
        std::memset(target, 0, MAX_TARGET_LENGTH);
        std::memset(parameters, 0, MAX_PARAMS_LENGTH);
        _padding[0] = _padding[1] = 0;
    }
};

/**
 * Frame timing information
 */
struct LUMINA_ALIGN(16) FrameTiming {
    float deltaTime;
    float totalTime;
    uint64_t frameCount;
    float fps;
    float gpuTime;
    float cpuTime;
    float _padding[2];

    FrameTiming()
        : deltaTime(0.0f)
        , totalTime(0.0f)
        , frameCount(0)
        , fps(0.0f)
        , gpuTime(0.0f)
        , cpuTime(0.0f)
        , _padding{0.0f, 0.0f} {}
};

/**
 * Main Lumina State - Central data contract
 * This structure is shared between Kotlin, C++, and Python layers
 * 
 * Total size should be a multiple of 256 bytes for optimal GPU buffer alignment
 */
struct LUMINA_ALIGN(256) LuminaState {
    // Version and identification (16 bytes)
    uint32_t version;
    uint32_t stateId;
    ProcessingState processingState;
    uint32_t flags;

    // Render configuration (16 bytes)
    RenderMode renderMode;
    uint32_t width;
    uint32_t height;
    float aspectRatio;

    // Viewport and camera (320 bytes with alignment)
    CameraState camera;
    
    // Active effects (up to 4 simultaneous) (256 bytes)
    std::array<EffectParams, 4> effects;
    uint32_t activeEffectCount;
    uint32_t _effectPadding[3];

    // UI styling (64 bytes)
    GlassmorphicParams uiStyle;

    // AI/ML state (720 bytes with alignment)
    AIIntent currentIntent;
    AIIntent pendingIntent;

    // Timing information (32 bytes)
    FrameTiming timing;

    // User input state (32 bytes)
    Vec2 touchPosition;
    Vec2 touchDelta;
    float touchPressure;
    uint32_t touchState; // 0: none, 1: down, 2: move, 3: up
    uint32_t _touchPadding[2];

    // Constructor with defaults
    LuminaState()
        : version(1)
        , stateId(0)
        , processingState(ProcessingState::IDLE)
        , flags(0)
        , renderMode(RenderMode::PASSTHROUGH)
        , width(1920)
        , height(1080)
        , aspectRatio(16.0f / 9.0f)
        , camera()
        , effects()
        , activeEffectCount(0)
        , _effectPadding{0, 0, 0}
        , uiStyle()
        , currentIntent()
        , pendingIntent()
        , timing()
        , touchPosition()
        , touchDelta()
        , touchPressure(0.0f)
        , touchState(0)
        , _touchPadding{0, 0} {}

    // Utility methods
    void incrementStateId() { stateId++; }
    bool isProcessing() const { return processingState == ProcessingState::PROCESSING; }
    void setDimensions(uint32_t w, uint32_t h) {
        width = w;
        height = h;
        aspectRatio = static_cast<float>(w) / static_cast<float>(h);
    }
};

// Static assertions to verify alignment and sizes
static_assert(sizeof(ColorRGBA) == 16, "ColorRGBA must be 16 bytes");
static_assert(sizeof(Vec2) == 16, "Vec2 must be 16 bytes (with padding)");
static_assert(sizeof(Vec3) == 16, "Vec3 must be 16 bytes (with padding)");
static_assert(sizeof(Mat4) == 64, "Mat4 must be 64 bytes");
static_assert(alignof(LuminaState) == 256, "LuminaState must have 256-byte alignment");

} // namespace lumina

#endif // LUMINA_ENGINE_STRUCTS_H
