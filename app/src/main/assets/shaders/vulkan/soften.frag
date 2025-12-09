#version 450
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 outColor;
layout(binding = 0) uniform sampler2D uTexture;
layout(push_constant) uniform PushConstants {
    float intensity;
    vec2 resolution;
} pushConstants;

void main() {
    vec2 texelSize = 1.0 / pushConstants.resolution;
    vec3 color = vec3(0.0);
    float totalWeight = 0.0;
    
    // Simple 3x3 box blur
    for (float y = -1.0; y <= 1.0; y++) {
        for (float x = -1.0; x <= 1.0; x++) {
            vec2 offset = vec2(x, y) * texelSize * pushConstants.intensity;
            color += texture(uTexture, vTexCoord + offset).rgb;
            totalWeight += 1.0;
        }
    }
    
    outColor = vec4(color / totalWeight, 1.0);
}
