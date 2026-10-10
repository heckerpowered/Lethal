#version 450
layout(location = 0) in vec3 position;
layout(location = 1) in vec4 state;
layout(location = 2) in vec2 arcState;
layout(push_constant) uniform Slash { mat4 clipFromLocal; vec4 motion; vec4 baseColor; } effect;
layout(location = 0) out vec4 trailState;
layout(location = 1) out vec4 fragmentClip;
layout(location = 2) out vec2 trailArcState;
void main() {
    gl_Position = effect.clipFromLocal * vec4(position, 1.0);
    trailState = state;
    fragmentClip = gl_Position;
    trailArcState = arcState;
}
