#version 450

layout (set = 0, binding = 0) uniform sampler2D image;
layout (location = 0) in vec2 coordinates;
layout (location = 0) out vec4 result;

layout (push_constant) uniform Surface {
    mat4 clipFromLocal;
    vec4 color;
} surface;

void main() {
    result = texture(image, coordinates) * surface.color;
    if (result.a < 0.5) discard;
}
