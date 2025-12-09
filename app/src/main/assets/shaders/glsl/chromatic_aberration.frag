#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 outColor;

uniform sampler2D uTexture;
uniform float uIntensity;
uniform vec2 uResolution;

void main() {
    float offset = 0.002 + 0.004 * uIntensity;
    vec2 centered = vTexCoord - 0.5;
    vec2 dir = normalize(centered + 0.0001) * offset;

    float r = texture(uTexture, vTexCoord - dir).r;
    float g = texture(uTexture, vTexCoord).g;
    float b = texture(uTexture, vTexCoord + dir).b;
    
    outColor = vec4(r, g, b, 1.0);
}
