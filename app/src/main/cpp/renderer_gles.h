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

private:
    bool ensurePipeline();
    bool compileShader(GLenum type, const char* source, GLuint& shaderOut);
    void destroyPipeline();

    GLuint glProgram_ = 0;
    GLuint glVbo_ = 0;
    GLuint glVao_ = 0;
    GLint uTimeLoc_ = -1;
    GLint uIntensityLoc_ = -1;
    bool pipelineReady_ = false;
    int surfaceWidth_ = 0;
    int surfaceHeight_ = 0;
};

#endif // LUMINA_RENDERER_GLES_H
