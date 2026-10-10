#version 450

layout(set = 0, binding = 0) uniform sampler2D image;
layout(push_constant) uniform Tent {
    vec2 texelSize;
} tentFilter;

layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;

void main() {
    vec4 total = vec4(0);
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            float weight = float((x == 0 ? 2 : 1) * (y == 0 ? 2 : 1));
            total += texture(image, coordinates + vec2(x, y) * tentFilter.texelSize) * weight;
        }

    result = total / 16.0;
}
