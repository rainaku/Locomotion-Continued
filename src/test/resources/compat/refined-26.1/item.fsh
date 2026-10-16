#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 unshadedColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * ColorModulator;

    ivec4 pixel = ivec4(round(texelFetch(Sampler0, ivec2(texCoord0 * textureSize(Sampler0, 0)), 0) * 255.0));
    int alphaRef = pixel.a;

    // Pixels com alpha especial = emissivo (sem iluminação, alpha forçado a 1)
    bool isEmissive = (alphaRef == 200 || alphaRef == 252 || alphaRef == 253 || alphaRef == 254);

    if (isEmissive) {
        color = vec4(color.rgb, 1.0);
    } else {
        color *= vertexColor;
    }

    if (color.a < 0.1) discard;

    fragColor = apply_fog(
        color,
        sphericalVertexDistance,
        cylindricalVertexDistance,
        FogEnvironmentalStart,
        FogEnvironmentalEnd,
        FogRenderDistanceStart,
        FogRenderDistanceEnd,
        FogColor
    );
}
