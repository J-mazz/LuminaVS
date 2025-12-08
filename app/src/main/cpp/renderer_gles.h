#ifndef LUMINA_RENDERER_GLES_H
#define LUMINA_RENDERER_GLES_H

#include <GLES3/gl3.h>

#include "engine_structs.h"

class GLRenderer {
public:
    GLRenderer() = default;
    ~GLRenderer();

    // Called after GL context is current
    bool initialize();
    void onSurfaceSize(int width, int height);
    bool render(const lumina::LuminaState& state);
    void onContextLost();
    void destroy();
    GLuint getInputTextureId();

private:
    bool ensurePipeline();
    bool ensureExternalTexture();
    bool compileShader(GLenum type, const char* source, GLuint& shaderOut);
    void destroyPipeline();

    GLuint glProgram_ = 0;
    GLuint glVbo_ = 0;
    GLuint glVao_ = 0;
    GLuint externalTex_ = 0;
    GLint uTimeLoc_ = -1;
    GLint uIntensityLoc_ = -1;
    GLint uEffectTypeLoc_ = -1;
    GLint uTintLoc_ = -1;
    GLint uCenterLoc_ = -1;
    GLint uScaleLoc_ = -1;
    GLint uParamsLoc_ = -1;
    GLint uResolutionLoc_ = -1;
    GLint uCameraTexLoc_ = -1;
    bool pipelineReady_ = false;
    int surfaceWidth_ = 0;
    int surfaceHeight_ = 0;
};

#endif // LUMINA_RENDERER_GLES_H
