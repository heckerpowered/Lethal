#version 450
#extension GL_GOOGLE_include_directive: require

layout(set = 0, binding = 0) uniform sampler2D content;
layout(set = 0, binding = 1) uniform sampler2D glow;
layout(set = 0, binding = 2) uniform sampler2D adaptedExposure;
layout (push_constant) uniform ToneMapping {
    vec4 mapping;
    vec4 controls;
} toneMapping;

layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;

#include "aces20.glsl"

vec3 encodeSrgb(vec3 rgb) {
    return mix(1.055 * pow(rgb, vec3(1.0 / 2.4)) - 0.055, 12.92 * rgb, lessThanEqual(rgb, vec3(0.0031308)));
}

void main() {
    vec4 surface = texture(content, coordinates);
    vec4 halo = texture(glow, coordinates) * toneMapping.controls.x;
    vec3 radiance = max(surface.rgb + halo.rgb, vec3(0.0));

    float exposure = toneMapping.mapping.x;

    if (toneMapping.controls.z > 0.5)
    exposure *= texture(adaptedExposure, vec2(0.5)).r;

    vec3 mapped = aces2DisplayLinear(vec4(radiance * exposure, 1.0)).rgb;

    if (toneMapping.mapping.y > 0.5)
    mapped = encodeSrgb(mapped);

    float totalEnergy = dot(radiance, vec3(0.2126, 0.7152, 0.0722));
    float share = totalEnergy > 0.0 ? clamp(dot(surface.rgb, vec3(0.2126, 0.7152, 0.0722)) / totalEnergy, 0.0, 1.0) : 0.0;

    // Encode the combined color once, then split; alpha remains coverage or numeric signal.
    result = toneMapping.controls.y > 1.5 ? vec4(mapped, surface.a)
    : toneMapping.controls.y > 0.5 ? vec4(mapped * (1.0 - share), halo.a)
    : vec4(mapped * share, surface.a);
}
