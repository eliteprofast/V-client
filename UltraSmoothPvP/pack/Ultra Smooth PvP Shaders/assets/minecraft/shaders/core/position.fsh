#version 150

#moj_import <minecraft:fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec3 localPos;

out vec4 fragColor;

void main() {
    vec4 color = ColorModulator;

    // Ultra Smooth PvP: the sky disc is the flat fan at exactly y = +16 around the camera.
    // Stars and everything else using this shader are not at that height, so they are untouched.
    if (abs(localPos.y - 16.0) < 0.01) {
        float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        float t = smoothstep(0.0, 190.0, length(localPos.xz));          // 0 = zenith, 1 = horizon
        vec3 zenith = color.rgb * vec3(0.86, 0.92, 1.05);                // slightly deeper and bluer overhead
        vec3 horizon = color.rgb + (1.0 - color.rgb) * (0.20 * lum);     // paler near the horizon, stays dark at night
        color.rgb = mix(zenith, horizon, t);
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
