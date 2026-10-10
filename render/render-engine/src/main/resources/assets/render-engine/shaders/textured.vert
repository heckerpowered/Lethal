#version 450

layout(location = 0) in vec3 position;
layout(location = 1) in vec2 uv;
layout(location = 0) out vec2 coordinates;

layout(push_constant) uniform Surface {
    mat4 clipFromLocal;
    vec4 color;
} surface;

void main() {
    gl_Position = surface.clipFromLocal * vec4(position, 1);
    coordinates = uv;
}
