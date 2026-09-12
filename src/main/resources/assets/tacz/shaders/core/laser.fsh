#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    // The 1.20.1 position/color/texture/lightmap shader did not sample its lightmap or apply fog.
    vec4 beam = texture(Sampler0, texCoord0) * vertexColor;
    if (beam.a < 0.1) discard;
    fragColor = beam * ColorModulator;
}
