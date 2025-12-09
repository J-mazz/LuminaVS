#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 outColor;

uniform sampler2D uTexture;
uniform float uIntensity;
uniform float uTime;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    vec3 originalColor = texture(uTexture, vTexCoord).rgb;
    float noise = (hash(vTexCoord + uTime) - 0.5) * 0.2 * uIntensity;
    outColor = vec4(originalColor + noise, 1.0);
}
