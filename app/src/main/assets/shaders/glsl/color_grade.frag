#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 outColor;

uniform sampler2D uTexture;
uniform float uIntensity;
uniform vec4 uTintColor;

void main() {
    vec3 originalColor = texture(uTexture, vTexCoord).rgb;
    vec3 tintedColor = mix(originalColor, uTintColor.rgb, uIntensity);
    outColor = vec4(tintedColor, 1.0);
}
