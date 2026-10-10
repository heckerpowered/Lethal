#version 450

layout(set = 0, binding = 0) uniform sampler2D image;
layout(push_constant) uniform Resample { vec4 sizes; } resample;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;

void main() {
    vec2 sourceSize = resample.sizes.xy;
    vec2 targetSize = resample.sizes.zw;
    vec2 pixel = min(floor(coordinates * targetSize), targetSize - 1.0);
    vec2 footprint = sourceSize / targetSize;
    vec2 lower = pixel * footprint;
    vec2 upper = (pixel + 1.0) * footprint;
    vec4 total = vec4(0.0);
    for (int y = int(floor(lower.y)); y < int(ceil(upper.y)); y++) {
        float height = max(0.0, min(upper.y, float(y + 1)) - max(lower.y, float(y)));
        for (int x = int(floor(lower.x)); x < int(ceil(upper.x)); x++) {
            float width = max(0.0, min(upper.x, float(x + 1)) - max(lower.x, float(x)));
            total += texture(image, (clamp(vec2(x, y), vec2(0.0), sourceSize - 1.0) + 0.5) / sourceSize) * width * height;
        }
    }
    result = total / (footprint.x * footprint.y);
}
