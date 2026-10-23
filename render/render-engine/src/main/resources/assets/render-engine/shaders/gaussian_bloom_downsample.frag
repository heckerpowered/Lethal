#version 450
layout(set = 0, binding = 0) uniform sampler2D image;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;
layout(push_constant) uniform Downsample { vec4 controls; } downsample;
void main() {
    if (downsample.controls.x < 0.5) { result = texture(image, coordinates); return; }
    vec2 offset = 1.0 / vec2(textureSize(image, 0));
    result = (texture(image, coordinates + offset) + texture(image, coordinates - offset)
        + texture(image, coordinates + vec2(offset.x, -offset.y))
        + texture(image, coordinates + vec2(-offset.x, offset.y))) * 0.25;
}
