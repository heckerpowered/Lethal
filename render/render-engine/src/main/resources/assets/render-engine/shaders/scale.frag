#version 450

layout(set = 0, binding = 0) uniform sampler2D image;
layout(push_constant) uniform Scale { float scale; } parameters;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;

void main() {
    result = texture(image, coordinates) * parameters.scale;
}
