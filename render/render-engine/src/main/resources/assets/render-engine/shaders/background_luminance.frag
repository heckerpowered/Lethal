#version 450

layout(set = 0, binding = 0) uniform sampler2D background;
layout (push_constant) uniform Metering {
    vec4 controls;
    vec4 sampling;
} metering;
layout(location = 0) in vec2 coordinates;

layout(location = 0) out vec4 result;

vec3 decodeSrgb(vec3 color) {
    return mix(pow((color + 0.055) / 1.055, vec3(2.4)), color / 12.92, lessThanEqual(color, vec3(0.04045)));
}

void main() {
    float luminance;
    if (metering.controls.y > 0.5)
    luminance = texture(background, coordinates).r;
    else {
        vec2 extent = vec2(textureSize(background, 0));
        vec2 cell = min(floor(coordinates * metering.sampling.xy), metering.sampling.xy - 1.0);
        vec2 lower = cell * extent / metering.sampling.xy;
        vec2 upper = (cell + 1.0) * extent / metering.sampling.xy;

        float weighted = 0.0;
        float area = 0.0;

        for (int y = int(floor(lower.y)); y < int(ceil(upper.y)); ++y)
        for (int x = int(floor(lower.x)); x < int(ceil(upper.x)); ++x) {
            vec2 coverage = max(min(upper, vec2(x + 1, y + 1)) - max(lower, vec2(x, y)), vec2(0.0));
            float weight = coverage.x * coverage.y;

            vec3 color = texture(background, (vec2(x, y) + 0.5) / extent).rgb;
            if (metering.controls.x > 0.5) {
                color = decodeSrgb(color);
            }

            weighted += dot(color, vec3(1.0 / 3.0)) * weight;
            area += weight;
        }

        luminance = weighted / max(area, 0.0001);
    }

    result = vec4(log2(max(luminance, exp2(metering.controls.z))), 0.0, 0.0, 0.0);
}
