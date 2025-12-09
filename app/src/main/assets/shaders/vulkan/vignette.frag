#version 450
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 outColor;
layout(binding = 0) uniform sampler2D uTexture;
layout(push_constant) uniform PushConstants {
    float intensity;
} pushConstants;

void main() {
    vec2 centeredTexCoord = vTexCoord - 0.5;
    float dist = length(centeredTexCoord);
    float vignette = 1.0 - smoothstep(0.2, 0.7, dist) * pushConstants.intensity;
    
    vec4 originalColor = texture(uTexture, vTexCoord);
    outColor = vec4(originalColor.rgb * vignette, originalColor.a);
}
