#version 450
layout (set = 0, binding = 0) uniform sampler2D image;
layout (location = 0) in vec2 coordinates;
layout (location = 0) out vec4 result;
layout (push_constant) uniform Beam {
    mat4 clipFromLocal;
    vec4 cornersA;
    vec4 cornersB;
    vec4 heightAndV;
    vec4 color;
} beam;
void main() { result = texture(image, coordinates) * beam.color; }
