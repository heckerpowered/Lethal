#version 450

layout(set = 0, binding = 0) uniform sampler2D image;
layout(push_constant) uniform Brightness {
    float threshold;
} bloom;

layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;

void main() {
    vec4 color = texture(image, coordinates);
    float brightness = dot(color.rgb, vec3(.2126, .7152, .0722));
    if (brightness < bloom.threshold) {
        result = vec4(0);
        return;
    }

    float softness = clamp(brightness - bloom.threshold, 0.0, 1.0);
    float delta = softness * softness * (3.0 - 2.0 * softness);
    float scale = delta * (brightness - bloom.threshold) / max(brightness, .00001);

    result = vec4(color.rgb * scale, color.a);
}
