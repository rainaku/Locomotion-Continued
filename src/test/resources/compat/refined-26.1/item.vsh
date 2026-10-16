#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 unshadedColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    vec4 lightmap = sample_lightmap(Sampler2, UV2);

    // vertexColor: iluminação direcional completa + lightmap
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color) * lightmap;

    // unshadedColor: cor base + lightmap (sem iluminação direcional)
    // passado ao fsh para uso interno, caso necessário
    unshadedColor = Color * lightmap;

    texCoord0 = UV0;
}
