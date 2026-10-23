#version 450
layout(set = 0, binding = 0) uniform sampler2D image;
layout(set = 0, binding = 1) uniform sampler2D auxiliary;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;
layout(push_constant) uniform Configuration { vec4 controls; vec4 dimensions; vec4 channel; } configuration;

vec4 pixel(sampler2D sourceImage, vec2 position, vec2 size) {
    return texture(sourceImage, (position + 0.5) / size);
}
float reverseBits(float value, float length) {
    float reversed = 0.0;
    for (int bit = 0; bit < 30; ++bit) {
        if (length < 1.5) break;
        reversed = reversed * 2.0 + mod(value, 2.0);
        value = floor(value * 0.5);
        length *= 0.5;
    }
    return reversed;
}
vec2 multiplyComplex(vec2 left, vec2 right) {
    return vec2(left.x * right.x - left.y * right.y, left.x * right.y + left.y * right.x);
}

void main() {
    // Tone mapping adds C once. UE supplies sceneFactor-1 and an already weighted scatter image.
    // Signed residuals must survive floating-point storage without clamping here.
    result = vec4(texture(auxiliary, coordinates).rgb + configuration.controls.rgb * texture(image, coordinates).rgb, 0.0);
}
