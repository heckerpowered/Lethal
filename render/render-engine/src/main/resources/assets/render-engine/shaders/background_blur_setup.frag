#version 450

layout (set = 0, binding = 0) uniform sampler2D background;
layout (push_constant) uniform Setup {
    vec4 controls;
} settings;
layout (location = 0) in vec2 coordinates;

layout (location = 0) out vec4 result;

void main() {
    ivec2 extent = textureSize(background, 0);
    ivec2 reduced = (extent + 7) / 8;
    ivec2 start = ivec2(coordinates * vec2(reduced)) * 8;

    float sum = 0.0;
    for (int y = 0; y < 8; ++y)
    for (int x = 0; x < 8; ++x) {
        ivec2 pixel = clamp(start + ivec2(x, y), ivec2(0), extent - 1);
        sum += texture(background, (vec2(pixel) + 0.5) / vec2(extent)).r;
    }

    result = vec4(log2(max(sum / 64.0, exp2(settings.controls.x))), 0.0, 0.0, 0.0);
}
