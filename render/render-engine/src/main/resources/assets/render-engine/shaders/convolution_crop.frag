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
    vec2 position = floor(coordinates * configuration.dimensions.zw);
    vec2 value = pixel(image, position + configuration.controls.xy, configuration.dimensions.xy).rb;
    vec3 previous = configuration.controls.z > 0.5 ? pixel(auxiliary, position, configuration.dimensions.zw).rgb : vec3(0.0);
    result = vec4(previous + vec3(value.x * configuration.channel.r, value.y * configuration.channel.g, value.x * configuration.channel.b), 0.0);
}
