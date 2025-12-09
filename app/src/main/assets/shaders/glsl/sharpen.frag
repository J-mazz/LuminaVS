#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 outColor;

uniform sampler2D uTexture;
uniform float uIntensity;
uniform vec2 uResolution;

void main() {
    vec2 texelSize = 1.0 / uResolution;
    vec3 originalColor = texture(uTexture, vTexCoord).rgb;
    
    vec3 sum = vec3(0.0);
    sum += texture(uTexture, vTexCoord - texelSize).rgb * -1.0;
    sum += texture(uTexture, vTexCoord + vec2(-texelSize.x, texelSize.y)).rgb * -1.0;
    sum += texture(uTexture, vTexCoord + vec2(texelSize.x, -texelSize.y)).rgb * -1.0;
    sum += texture(uTexture, vTexCoord + texelSize).rgb * -1.0;
    sum += originalColor * 4.0;
    
    outColor = vec4(mix(originalColor, originalColor + sum, uIntensity), 1.0);
}
