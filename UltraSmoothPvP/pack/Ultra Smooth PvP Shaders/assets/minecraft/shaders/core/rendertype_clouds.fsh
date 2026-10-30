#version 150

#moj_import <minecraft:fog.glsl>

uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor;

    // Ultra Smooth PvP: lift the baked-in side/bottom shading so clouds look soft and fluffy,
    // add a faint cool tint, and fade them out near the fog edge instead of a hard cut.
    color.rgb = pow(color.rgb, vec3(0.82)) * vec3(0.99, 1.0, 1.03);
    color.a *= 1.0 - smoothstep(FogEnd * 0.55, FogEnd, vertexDistance);

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
