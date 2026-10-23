#version 450
layout(set = 0, binding = 0) uniform sampler2D image;
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
    vec2 reversed = vec2(reverseBits(position.x, configuration.dimensions.x), reverseBits(position.y, configuration.dimensions.y));
    float value = 0.0;
    float secondValue = 0.0;
    if (all(lessThan(reversed, configuration.dimensions.zw))) {
        vec3 rgb = pixel(image, reversed, configuration.dimensions.zw).rgb;
        float luminance = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
        if (configuration.controls.x < configuration.controls.y && luminance > configuration.controls.x)
            rgb *= min(configuration.controls.z * (luminance - configuration.controls.x) +
                configuration.controls.x, configuration.controls.y) / luminance;
        value = dot(rgb, configuration.channel.rgb);
        if (configuration.channel.a > 0.5) secondValue = dot(rgb, vec3(0.0, 1.0, 0.0));
    }
    result = vec4(value, 0.0, secondValue, 0.0);
}
