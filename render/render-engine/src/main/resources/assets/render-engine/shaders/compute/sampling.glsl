vec4 fetchZero(ivec2 pixel) {
    ivec2 size = imageSize(inputImage);
    if (any(lessThan(pixel, ivec2(0))) || any(greaterThanEqual(pixel, size))) return vec4(0);
    return imageLoad(inputImage, storagePosition(pixel, imageSize(inputImage)));
}

vec4 fetchClamped(ivec2 pixel) {
    return imageLoad(inputImage, storagePosition(clamp(pixel, ivec2(0), imageSize(inputImage) - 1), imageSize(inputImage)));
}

vec4 sampleZero(vec2 uv) {
    vec2 position = uv * vec2(imageSize(inputImage)) - .5;
    ivec2 pixel = ivec2(floor(position));
    vec2 fraction = fract(position);
    return mix(
        mix(fetchZero(pixel), fetchZero(pixel + ivec2(1, 0)), fraction.x),
        mix(fetchZero(pixel + ivec2(0, 1)), fetchZero(pixel + ivec2(1, 1)), fraction.x), fraction.y
    );
}

vec4 sampleClamped(vec2 uv) {
    vec2 position = uv * vec2(imageSize(inputImage)) - .5;
    ivec2 pixel = ivec2(floor(position));
    vec2 fraction = fract(position);
    return mix(
        mix(fetchClamped(pixel), fetchClamped(pixel + ivec2(1, 0)), fraction.x),
        mix(fetchClamped(pixel + ivec2(0, 1)), fetchClamped(pixel + ivec2(1, 1)), fraction.x), fraction.y
    );
}
