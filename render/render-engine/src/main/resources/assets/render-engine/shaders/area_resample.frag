#version 450

layout(set = 0, binding = 0) uniform sampler2D image;
layout (push_constant) uniform Resample {
    vec4 sizes;
} resample;
layout(location = 0) in vec2 coordinates;

layout(location = 0) out vec4 result;

void main() {
    vec2 sourceSize = resample.sizes.xy;
    vec2 targetSize = resample.sizes.zw;
    vec2 pixel = min(floor(coordinates * targetSize), targetSize - 1.0);
    vec2 footprint = sourceSize / targetSize;
    vec2 lower = pixel * footprint;
    vec2 upper = (pixel + 1.0) * footprint;

    // Integrate the bilinear reconstruction, splitting at source texel centers.
    // Texel-center box samples would replicate the coarse grid during enlargement.
    vec4 mean = vec4(0.0);
    float accumulatedArea = 0.0;

    for (int y = int(floor(lower.y + 0.5)); y < int(ceil(upper.y + 0.5)); y++) {
        for (int x = int(floor(lower.x + 0.5)); x < int(ceil(upper.x + 0.5)); x++) {
            vec2 segmentLower = max(lower, vec2(x, y) - 0.5);
            vec2 segmentUpper = min(upper, vec2(x, y) + 0.5);
            vec2 extent = max(segmentUpper - segmentLower, vec2(0.0));
            float area = extent.x * extent.y;
            if (area > 0.0) {
                vec4 sampleColor = texture(image, (segmentLower + segmentUpper) * 0.5 / sourceSize);
                // A running convex average keeps finite HDR samples inside their input range.
                accumulatedArea += area;
                mean += (sampleColor - mean) * (area / accumulatedArea);
            }
        }
    }

    result = mean;
}
