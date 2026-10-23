#version 450
layout(set = 0, binding = 0) uniform sampler2D image;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;
layout(push_constant) uniform Axis { vec4 controls; } axis;

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
    float radius = axis.controls.z;
    int limit = int(ceil(radius));
    vec4 total = vec4(0.0);
    float mass = 0.0;
    // Pair adjacent discrete weights into one fractional lookup; normalize the filter only.
    for (int first = -limit; first <= limit; first += 2) {
        float x = float(first) / radius;
        float a = exp(axis.controls.w * x * x);
        float b = 0.0;
        if (first < limit) { float y = float(first + 1) / radius; b = exp(axis.controls.w * y * y); }
        float weight = a + b;
        if (weight > 0.0) {
            float position = float(first) + b / weight;
            total += sampleBorder(image, coordinates + position * axis.controls.xy) * weight;
            mass += weight;
        }
    }
    result = total / mass;
}
