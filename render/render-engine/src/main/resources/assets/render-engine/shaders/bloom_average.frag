#version 450

layout(set = 0, binding = 0) uniform sampler2D coarse;
layout(set = 0, binding = 1) uniform sampler2D fine;
layout(push_constant) uniform Average { vec2 weights; } average;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;

void main() {
    // A convex combination stays within the sampled HDR range before storage.
    result = texture(coarse, coordinates) * average.weights.x + texture(fine, coordinates) * average.weights.y;
}
