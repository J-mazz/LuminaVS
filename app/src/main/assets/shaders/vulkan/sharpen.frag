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
    vec3 originalColor = texture(uTexture, vTexCoord).rgb;
    
    vec3 sum = vec3(0.0);
    sum += texture(uTexture, vTexCoord - texelSize).rgb * -1.0;
    sum += texture(uTexture, vTexCoord + vec2(-texelSize.x, texelSize.y)).rgb * -1.0;
    sum += texture(uTexture, vTexCoord + vec2(texelSize.x, -texelSize.y)).rgb * -1.0;
    sum += texture(uTexture, vTexCoord + texelSize).rgb * -1.0;
    sum += originalColor * 4.0;
    
    outColor = vec4(mix(originalColor, originalColor + sum, pushConstants.intensity), 1.0);
}
