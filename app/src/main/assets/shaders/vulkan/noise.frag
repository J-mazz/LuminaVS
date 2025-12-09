#version 450
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 outColor;
layout(binding = 0) uniform sampler2D uTexture;
layout(push_constant) uniform PushConstants {
    float intensity;
    float time;
} pushConstants;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    vec3 originalColor = texture(uTexture, vTexCoord).rgb;
    float noise = (hash(vTexCoord + pushConstants.time) - 0.5) * 0.2 * pushConstants.intensity;
    outColor = vec4(originalColor + noise, 1.0);
}
