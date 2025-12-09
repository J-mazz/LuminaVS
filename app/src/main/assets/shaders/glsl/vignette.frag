#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 outColor;

uniform sampler2D uTexture;
uniform float uVignetteIntensity; // 0.0 to 1.0

void main() {
    vec2 centeredTexCoord = vTexCoord - 0.5;
    float dist = length(centeredTexCoord);
    float vignette = 1.0 - smoothstep(0.2, 0.7, dist) * uVignetteIntensity;
    
    vec4 originalColor = texture(uTexture, vTexCoord);
    outColor = vec4(originalColor.rgb * vignette, originalColor.a);
}
