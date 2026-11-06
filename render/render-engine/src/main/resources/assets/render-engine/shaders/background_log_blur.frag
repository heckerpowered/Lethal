#version 450

layout(set = 0, binding = 0) uniform sampler2D background;
layout (push_constant) uniform Blur {
    vec4 controls;
} blur;
layout(location = 0) in vec2 coordinates;

layout(location = 0) out vec4 result;

void main() {
    vec2 extent = vec2(textureSize(background, 0));
    vec2 axis = blur.controls.x > 0.5 ? vec2(1.0 / extent.x, 0.0) : vec2(0.0, 1.0 / extent.y);
    float radius = blur.controls.y;
    int taps = int(ceil(radius));

    float sum = 0.0;
    float weights = 0.0;

    for (int i = -taps; i <= taps; i += 2) {
        float first = exp(-16.7 * pow(float(i) / radius, 2.0));
        float second = i == taps ? 0.0 : exp(-16.7 * pow(float(i + 1) / radius, 2.0));
        float weight = first + second;
        if (weight == 0.0) {
            continue;
        }

        float offset = float(i) + second / weight;
        vec2 uv = vec2(1.0) - abs(mod(coordinates + offset * axis, vec2(2.0)) - vec2(1.0));
        sum += texture(background, uv).r * weight;
        weights += weight;
    }

    result = vec4(sum / weights, 0.0, 0.0, 0.0);
}
