#version 450

layout(location = 0) in vec3 position;

layout(push_constant) uniform Surface {
    mat4 clipFromLocal;
    vec4 color;
} surface;

void main() {
    gl_Position = surface.clipFromLocal * vec4(position, 1);
}
