#include "renderer_gles.h"

#include <android/log.h>

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

    glUseProgram(glProgram_);
    float intensity = (state.activeEffectCount > 0) ? state.effects[0].intensity : 1.0f;
    if (uTimeLoc_ >= 0) glUniform1f(uTimeLoc_, state.timing.totalTime);
    if (uIntensityLoc_ >= 0) glUniform1f(uIntensityLoc_, intensity);

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
precision mediump float;
in vec2 vUv;
out vec4 fragColor;
uniform float uTime;
uniform float uIntensity;
void main(){
    vec3 base = mix(vec3(0.07, 0.11, 0.18), vec3(0.10, 0.20, 0.35), vUv.y);
    float ripple = 0.04 * sin(uTime * 1.5 + vUv.x * 6.28318);
    float vignette = smoothstep(0.9, 0.4, length(vUv - 0.5));
    base += ripple;
    base *= mix(0.85, 1.15, vignette);
    base *= (0.8 + uIntensity * 0.25);
    fragColor = vec4(base, 1.0);
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
    uTimeLoc_ = uIntensityLoc_ = -1;
    pipelineReady_ = false;
}
