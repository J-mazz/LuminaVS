#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 outColor;

uniform sampler2D uTexture;
uniform float uIntensity;
uniform vec2 uResolution;

void main() {
    vec2 texelSize = 1.0 / uResolution;
    vec3 color = vec3(0.0);
    float totalWeight = 0.0;
    
    // Simple 3x3 box blur
    for (float y = -1.0; y <= 1.0; y++) {
        for (float x = -1.0; x <= 1.0; x++) {
            vec2 offset = vec2(x, y) * texelSize * uIntensity;
            color += texture(uTexture, vTexCoord + offset).rgb;
            totalWeight += 1.0;
        }
    }
    
    outColor = vec4(color / totalWeight, 1.0);
}
