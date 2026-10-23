#version 450
layout(set = 0, binding = 0) uniform sampler2D image;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;
layout(set = 0, binding = 1) uniform sampler2D coarse;
layout(push_constant) uniform Accumulation { vec4 controls; vec4 gain; } accumulation;

vec4 fetchBorder(sampler2D sourceImage, ivec2 pixel) {
    ivec2 dimensions = textureSize(sourceImage, 0);
    if (any(lessThan(pixel, ivec2(0))) || any(greaterThanEqual(pixel, dimensions))) return vec4(0.0);
    return texture(sourceImage, (vec2(pixel) + 0.5) / vec2(dimensions));
}
vec4 sampleBorder(sampler2D sourceImage, vec2 uv) {
    vec2 position = uv * vec2(textureSize(sourceImage, 0)) - 0.5;
    ivec2 pixel = ivec2(floor(position));
    vec2 fraction = fract(position);
    return mix(mix(fetchBorder(sourceImage, pixel), fetchBorder(sourceImage, pixel + ivec2(1, 0)), fraction.x),
               mix(fetchBorder(sourceImage, pixel + ivec2(0, 1)), fetchBorder(sourceImage, pixel + ivec2(1, 1)), fraction.x), fraction.y);
}

void main() {
    vec3 wider = accumulation.controls.x > 0.5 ? sampleBorder(coarse, coordinates).rgb : vec3(0.0);
    result = vec4(texture(image, coordinates).rgb * accumulation.gain.rgb + wider, 0.0);
}
