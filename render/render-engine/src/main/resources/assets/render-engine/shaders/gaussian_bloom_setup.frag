#version 450
layout(set = 0, binding = 0) uniform sampler2D image;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;
layout(push_constant) uniform Setup { vec4 controls; } setup;
void main() {
    vec3 radiance = texture(image, coordinates).rgb;
    float amount = setup.controls.x == -1.0 ? 1.0
        : clamp((dot(radiance, vec3(0.2126, 0.7152, 0.0722)) - setup.controls.x) * 0.5, 0.0, 1.0);
    result = vec4(radiance * amount, 0.0);
}
