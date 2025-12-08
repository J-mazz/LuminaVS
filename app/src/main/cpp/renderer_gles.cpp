#include "renderer_gles.h"

#include <android/log.h>
#include <GLES2/gl2ext.h>

#define LOG_TAG "LuminaRenderer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

GLRenderer::~GLRenderer() {
    destroy();
}

bool GLRenderer::initialize() {
    pipelineReady_ = false;
    return true;
}

void GLRenderer::onSurfaceSize(int width, int height) {
    surfaceWidth_ = width;
    surfaceHeight_ = height;
    pipelineReady_ = false; // force pipeline rebuild after resize/context restore
}

bool GLRenderer::render(const lumina::LuminaState& state) {
    if (surfaceWidth_ <= 0 || surfaceHeight_ <= 0) return false;

    glViewport(0, 0, surfaceWidth_, surfaceHeight_);
    glDisable(GL_DEPTH_TEST);
    glClearColor(0.05f, 0.05f, 0.08f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    if (!ensurePipeline()) return false;
    if (!ensureExternalTexture()) return false;

    glUseProgram(glProgram_);
    const lumina::EffectParams* activeEffect = (state.activeEffectCount > 0) ? &state.effects[0] : nullptr;
    float intensity = activeEffect ? activeEffect->intensity : 1.0f;
    int effectType = activeEffect ? static_cast<int>(activeEffect->type) : 0;

    if (uTimeLoc_ >= 0) glUniform1f(uTimeLoc_, state.timing.totalTime);
    if (uIntensityLoc_ >= 0) glUniform1f(uIntensityLoc_, intensity);
    if (uEffectTypeLoc_ >= 0) glUniform1i(uEffectTypeLoc_, effectType);
    if (uTintLoc_ >= 0 && activeEffect) {
        const auto& c = activeEffect->tintColor;
        glUniform4f(uTintLoc_, c.r, c.g, c.b, c.a);
    }
    if (uCenterLoc_ >= 0 && activeEffect) glUniform2f(uCenterLoc_, activeEffect->center.x, activeEffect->center.y);
    if (uScaleLoc_ >= 0 && activeEffect) glUniform2f(uScaleLoc_, activeEffect->scale.x, activeEffect->scale.y);
    if (uParamsLoc_ >= 0 && activeEffect) glUniform2f(uParamsLoc_, activeEffect->param1, activeEffect->param2);
    if (uResolutionLoc_ >= 0) glUniform2f(uResolutionLoc_, static_cast<float>(surfaceWidth_), static_cast<float>(surfaceHeight_));
    if (uCameraTexLoc_ >= 0) glUniform1i(uCameraTexLoc_, 0);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, externalTex_);

    glBindVertexArray(glVao_);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindVertexArray(0);

    return true;
}

void GLRenderer::onContextLost() {
    destroyPipeline();
}

void GLRenderer::destroy() {
    destroyPipeline();
}

bool GLRenderer::ensurePipeline() {
    if (pipelineReady_) return true;

    destroyPipeline();

    const char* vsSrc = R"(#version 300 es
layout(location = 0) in vec2 aPos;
out vec2 vUv;
void main(){
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
})";

    const char* fsSrc = R"(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vUv;
out vec4 fragColor;
uniform float uTime;
uniform float uIntensity;
uniform int uEffectType;
uniform vec4 uTintColor;
uniform vec2 uEffectCenter;
uniform vec2 uEffectScale;
uniform vec2 uEffectParams;
uniform vec2 uResolution;
uniform samplerExternalOES uCameraTex;

float hash21(vec2 p){
    p = fract(p * vec2(234.34, 123.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

void main(){
    vec2 uv = vUv;
    vec2 centered = (uv - uEffectCenter) * uEffectScale;
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    centered.x *= aspect;

    vec3 base = texture(uCameraTex, uv).rgb;
    float ripple = 0.04 * sin(uTime * 1.5 + uv.x * 6.28318);
    base += ripple;

    float vignette = smoothstep(0.95, 0.45, length(centered));
    base = mix(base * 0.9, base, vignette);

    vec3 color = base;

    if (uEffectType == 1) { // BLUR-ish soften (cheap)
        float blurAmt = clamp(uIntensity, 0.0, 1.5) * 0.35;
        color = mix(color, vec3(dot(color, vec3(0.333))), blurAmt);
    } else if (uEffectType == 2) { // BLOOM halo
        float halo = exp(-dot(centered, centered) * (4.0 + uEffectParams.x * 2.0));
        color += halo * uIntensity * 0.6;
    } else if (uEffectType == 3) { // COLOR_GRADE tint
        color = mix(color, uTintColor.rgb, clamp(uIntensity, 0.0, 1.5));
        color *= 1.0 + uEffectParams.x * 0.1;
    } else if (uEffectType == 4) { // VIGNETTE
        float vig = smoothstep(0.8, 0.2, length(centered));
        color *= mix(1.0, vig, clamp(uIntensity, 0.0, 1.5));
    } else if (uEffectType == 5) { // CHROMATIC_ABERRATION stylized
        float offset = 0.002 + 0.004 * uIntensity;
        vec2 dir = normalize(centered + 0.0001) * offset;
        vec3 ca = vec3(
            base.r + ripple,
            base.g,
            base.b - ripple
        );
        ca += vec3(hash21(uv + dir), hash21(uv - dir), hash21(uv + dir.yx)) * 0.02 * uIntensity;
        color = mix(color, ca, 0.5);
    } else if (uEffectType == 6) { // NOISE
        float n = hash21(uv * uResolution + uTime * 0.5);
        float grain = (n - 0.5) * 0.18 * uIntensity;
        color += grain;
    } else if (uEffectType == 7) { // SHARPEN/contrast
        float c = dot(color, vec3(0.333));
        color = mix(vec3(c), color * 1.2, clamp(0.5 + uIntensity * 0.5, 0.0, 1.5));
    }

    color *= (0.8 + uIntensity * 0.25);
    fragColor = vec4(color, 1.0);
})";

    GLuint vs = 0, fs = 0;
    if (!compileShader(GL_VERTEX_SHADER, vsSrc, vs)) return false;
    if (!compileShader(GL_FRAGMENT_SHADER, fsSrc, fs)) { glDeleteShader(vs); return false; }

    glProgram_ = glCreateProgram();
    glAttachShader(glProgram_, vs);
    glAttachShader(glProgram_, fs);
    glLinkProgram(glProgram_);

    GLint linked = GL_FALSE;
    glGetProgramiv(glProgram_, GL_LINK_STATUS, &linked);
    glDeleteShader(vs);
    glDeleteShader(fs);
    if (linked != GL_TRUE) {
        char logBuf[512];
        glGetProgramInfoLog(glProgram_, sizeof(logBuf), nullptr, logBuf);
        LOGE("Program link failed: %s", logBuf);
        glDeleteProgram(glProgram_);
        glProgram_ = 0;
        return false;
    }

    static const GLfloat quadVertices[] = {
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f,
    };

    glGenVertexArrays(1, &glVao_);
    glGenBuffers(1, &glVbo_);
    glBindVertexArray(glVao_);
    glBindBuffer(GL_ARRAY_BUFFER, glVbo_);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quadVertices), quadVertices, GL_STATIC_DRAW);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(GLfloat), (void*)0);
    glEnableVertexAttribArray(0);
    glBindVertexArray(0);

    uTimeLoc_ = glGetUniformLocation(glProgram_, "uTime");
    uIntensityLoc_ = glGetUniformLocation(glProgram_, "uIntensity");
    uEffectTypeLoc_ = glGetUniformLocation(glProgram_, "uEffectType");
    uTintLoc_ = glGetUniformLocation(glProgram_, "uTintColor");
    uCenterLoc_ = glGetUniformLocation(glProgram_, "uEffectCenter");
    uScaleLoc_ = glGetUniformLocation(glProgram_, "uEffectScale");
    uParamsLoc_ = glGetUniformLocation(glProgram_, "uEffectParams");
    uResolutionLoc_ = glGetUniformLocation(glProgram_, "uResolution");
    uCameraTexLoc_ = glGetUniformLocation(glProgram_, "uCameraTex");

    pipelineReady_ = true;
    return true;
}

bool GLRenderer::compileShader(GLenum type, const char* source, GLuint& shaderOut) {
    shaderOut = glCreateShader(type);
    glShaderSource(shaderOut, 1, &source, nullptr);
    glCompileShader(shaderOut);
    GLint compiled = GL_FALSE;
    glGetShaderiv(shaderOut, GL_COMPILE_STATUS, &compiled);
    if (compiled != GL_TRUE) {
        char logBuf[512];
        glGetShaderInfoLog(shaderOut, sizeof(logBuf), nullptr, logBuf);
        LOGE("Shader compile failed: %s", logBuf);
        glDeleteShader(shaderOut);
        shaderOut = 0;
        return false;
    }
    return true;
}

void GLRenderer::destroyPipeline() {
    if (glVbo_) { glDeleteBuffers(1, &glVbo_); glVbo_ = 0; }
    if (glVao_) { glDeleteVertexArrays(1, &glVao_); glVao_ = 0; }
    if (glProgram_) { glDeleteProgram(glProgram_); glProgram_ = 0; }
    if (externalTex_) { glDeleteTextures(1, &externalTex_); externalTex_ = 0; }
    uTimeLoc_ = uIntensityLoc_ = uEffectTypeLoc_ = uTintLoc_ = uCenterLoc_ = uScaleLoc_ = uParamsLoc_ = uResolutionLoc_ = uCameraTexLoc_ = -1;
    pipelineReady_ = false;
}

bool GLRenderer::ensureExternalTexture() {
    if (externalTex_ != 0) return true;

    glGenTextures(1, &externalTex_);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, externalTex_);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    return externalTex_ != 0;
}
