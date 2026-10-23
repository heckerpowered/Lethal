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
    vec2 position = floor(coordinates * configuration.dimensions.xy);
    bool horizontal = configuration.controls.x > 0.5;
    float index = horizontal ? position.x : position.y;
    float span = configuration.controls.y;
    float local = mod(index, span * 0.5);
    float leftIndex = floor(index / span) * span + local;
    vec2 leftPosition = horizontal ? vec2(leftIndex, position.y) : vec2(position.x, leftIndex);
    vec2 rightPosition = leftPosition + (horizontal ? vec2(span * 0.5, 0.0) : vec2(0.0, span * 0.5));
    vec4 left = pixel(image, leftPosition, configuration.dimensions.xy);
    vec4 right = pixel(image, rightPosition, configuration.dimensions.xy);
    vec2 twiddle = pixel(auxiliary, vec2(local * configuration.dimensions.z / span, 0.0), vec2(configuration.dimensions.z, 1.0)).rg;
    twiddle.y *= configuration.controls.z;
    float sign = mod(index, span) < span * 0.5 ? 1.0 : -1.0;
    result = vec4((left.rg + sign * multiplyComplex(twiddle, right.rg)) * configuration.controls.w,
        (left.ba + sign * multiplyComplex(twiddle, right.ba)) * configuration.controls.w);
}
