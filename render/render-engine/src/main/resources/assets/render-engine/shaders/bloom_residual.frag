#version 450

layout(set = 0, binding = 0) uniform sampler2D image;
layout(set = 0, binding = 1) uniform sampler2D extracted;
layout(push_constant) uniform Scattering { float fraction; } scattering;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;

void main() {
    vec4 color = texture(image, coordinates);
    vec3 bright = texture(extracted, coordinates).rgb;
    result = vec4(color.rgb - scattering.fraction * bright, color.a);
}
