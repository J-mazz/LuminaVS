#version 450
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 outColor;
layout(binding = 0) uniform sampler2D uTexture;
layout(push_constant) uniform PushConstants {
    float intensity;
    vec4 tintColor;
} pushConstants;

void main() {
    vec3 originalColor = texture(uTexture, vTexCoord).rgb;
    vec3 tintedColor = mix(originalColor, pushConstants.tintColor.rgb, pushConstants.intensity);
    outColor = vec4(tintedColor, 1.0);
}
