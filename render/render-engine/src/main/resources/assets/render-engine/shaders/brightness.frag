#version 450

layout(set = 0, binding = 0) uniform sampler2D image;
layout(push_constant) uniform Brightness {
    float threshold;
} bloom;

layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;

void main() {
    vec4 color = texture(image, coordinates);
    if (bloom.threshold == -1.0) {
        result = color;
        return;
    }
    float brightness = dot(color.rgb, vec3(.2126, .7152, .0722));
    if (brightness < bloom.threshold) {
        result = vec4(0);
        return;
    }

    // Extract excess luminance without an additional near-threshold attenuation.
    // A second smooth weight suppresses dim colored halos before they can scatter.
    float scale = (brightness - bloom.threshold) / max(brightness, .00001);

    result = vec4(color.rgb * scale, color.a);
}
