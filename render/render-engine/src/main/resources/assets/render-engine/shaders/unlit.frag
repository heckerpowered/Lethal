#version 450

layout(push_constant) uniform Surface {
    mat4 clipFromLocal;
    vec4 color;
} surface;

layout(location = 0) out vec4 result;

void main() {
    result = surface.color;
}
