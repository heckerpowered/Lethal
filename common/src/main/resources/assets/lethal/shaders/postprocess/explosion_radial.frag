#version 450

layout (location = 0) in vec2 coordinates;

layout (location = 0) out vec4 result;
layout (set = 0, binding = 0) uniform sampler2D source;
layout (set = 0, binding = 1) uniform sampler2D worldDepth;
layout (push_constant) uniform Radial {
    vec4 radial;
    vec4 viewport;
    vec4 depth;
} settings;

// 90px at half-pixel spacing needs 180 taps; keep the spacing when extending the distance.
const int MaximumRadialSamples = 192;

void main() {
    vec4 sharp = texture(source, coordinates);
    vec2 viewportSize = max(settings.viewport.xy, vec2(1.0));
    vec2 deltaPixels = (coordinates - settings.radial.xy) * viewportSize;
    float radiusPixels = length(deltaPixels);
    float coreRadiusPixels = settings.radial.w;
    float blurMask = smoothstep(coreRadiusPixels, coreRadiusPixels + 20.0, radiusPixels);
    float lengthPixels = min(settings.radial.z, settings.viewport.z) * blurMask;
    // Do not cross the origin, where the outward direction is undefined.
    lengthPixels = min(lengthPixels, radiusPixels * 0.65);
    if (lengthPixels < settings.viewport.w * 0.5) {
        result = sharp;
        return;
    }

    vec2 directionUv = deltaPixels / max(radiusPixels, 0.0001) / viewportSize;
    int sampleCount = min(MaximumRadialSamples, max(1, int(ceil(lengthPixels / settings.viewport.w))));
    float targetDepth = texture(worldDepth, coordinates).r;
    vec4 integrated = vec4(0.0);
    float weightSum = 0.0;

    for (int index = 0; index < MaximumRadialSamples; index++) {
        if (index >= sampleCount)
        break;
        float fraction = (float(index) + 0.5) / float(sampleCount);
        float weight = 0.5 + 0.5 * cos(3.14159265 * fraction);
        vec2 sampleCoordinates = coordinates - directionUv * (lengthPixels * fraction);
        vec4 sampleColor = sharp;

        bool inside =
        all(greaterThanEqual(sampleCoordinates, vec2(0.0))) && all(lessThanEqual(sampleCoordinates, vec2(1.0)));
        if (inside) {
            float sampleDepth = texture(worldDepth, sampleCoordinates).r;
            // Reject samples behind the destination surface in the active depth convention.
            if ((sampleDepth - targetDepth) * settings.depth.x <= 0.00001)
            sampleColor = texture(source, sampleCoordinates);
        }

        // Rejected taps use the local source, avoiding border clamps and occlusion renormalization.
        integrated += sampleColor * weight;
        weightSum += weight;
    }

    result = mix(sharp, integrated / max(weightSum, 0.000001), blurMask);
}
