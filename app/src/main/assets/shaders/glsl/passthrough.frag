#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 outColor;

uniform sampler2D uTexture;

void main() {
    outColor = texture(uTexture, vTexCoord);
}
