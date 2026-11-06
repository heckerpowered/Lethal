#version 450

layout(set = 0, binding = 0) uniform sampler2D background;
layout(set = 0, binding = 1) uniform sampler2D previousJoint;
layout (push_constant) uniform Metering {
    vec4 controls;
} metering;
layout(location = 0) in vec2 coordinates;

layout(location = 0) out vec4 result;

vec3 decodeSrgb(vec3 color) {
    return mix(pow((color + 0.055) / 1.055, vec3(2.4)), color / 12.92, lessThanEqual(color, vec3(0.04045)));
}

void main() {
    vec2 extent = vec2(textureSize(background, 0));
    vec2 cell = min(floor(coordinates * metering.controls.zw), metering.controls.zw - 1.0);
    vec2 lower = cell * extent / metering.controls.zw;
    vec2 upper = (cell + 1.0) * extent / metering.controls.zw;

    float mean = 0.0;
    float accumulatedArea = 0.0;

    for (int y = int(floor(lower.y)); y < int(ceil(upper.y)); ++y) {
        for (int x = int(floor(lower.x)); x < int(ceil(upper.x)); ++x) {
            vec2 coverage = max(min(upper, vec2(x + 1, y + 1)) - max(lower, vec2(x, y)), vec2(0.0));
            float area = coverage.x * coverage.y;
            if (area <= 0.0) {
                continue;
            }

            vec3 color = texture(background, (vec2(x, y) + 0.5) / extent).rgb;
            if (metering.controls.x > 0.5) {
                color = decodeSrgb(color);
            }

            float value = dot(color, vec3(1.0 / 3.0));
            accumulatedArea += area;
            mean += (value - mean) * (area / accumulatedArea);
        }
    }

    float previous = metering.controls.y > 0.5 ? texture(previousJoint, coordinates).r : 0.0;
    result = vec4(previous + mean, 0.0, 0.0, 0.0);
}
