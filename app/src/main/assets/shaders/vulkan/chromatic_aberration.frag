#version 450
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 outColor;
layout(binding = 0) uniform sampler2D uTexture;
layout(push_constant) uniform PushConstants {
    float intensity;
} pushConstants;

void main() {
    float offset = 0.002 + 0.004 * pushConstants.intensity;
    vec2 centered = vTexCoord - 0.5;
    vec2 dir = normalize(centered + 0.0001) * offset;

    float r = texture(uTexture, vTexCoord - dir).r;
    float g = texture(uTexture, vTexCoord).g;
    float b = texture(uTexture, vTexCoord + dir).b;
    
    outColor = vec4(r, g, b, 1.0);
}
