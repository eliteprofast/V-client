#version 150

#moj_import <minecraft:fog.glsl>

in vec3 Position;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;
uniform int FogShape;

out float vertexDistance;
out vec3 localPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(Position, FogShape);
    localPos = Position; // Ultra Smooth PvP: needed for the sky gradient
}
