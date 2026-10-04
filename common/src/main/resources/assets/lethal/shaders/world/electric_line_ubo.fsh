#version 450

layout (std140, set = 1, binding = 0) uniform ElectricLineStyle {
    float coreHalfWidthPixels;
    float ribbonHalfWidthPixels;
    float spikeReachPixels;
    float lightningIntensity;
    float lightningSpikeDensity;
    float lightningAnimationFrequency;
};

layout (push_constant) uniform ElectricLineAnimation {
    layout (offset = 48) float animationTimeSeconds;
};

layout (location = 0) in vec2 beamStartPixels;
layout (location = 1) in vec2 beamDirectionPixels;
layout (location = 2) in float beamLengthPixels;
layout (location = 3) in vec4 beamColor;
layout (location = 4) in vec3 fragmentPixelHomogeneous;
layout (location = 0) out vec4 result;

float randomValue(float seed) {
    return fract(sin(seed * 12.9898) * 43758.5453);
}

float spike(float positionPixels, float spacingPixels, float seed) {
    float cellPosition = positionPixels / spacingPixels;
    float cellIdentifier = floor(cellPosition);
    float positionWithinCell = fract(cellPosition);
    float peakPosition = mix(0.25, 0.75, randomValue(cellIdentifier + seed));
    float risingEdge = positionWithinCell / peakPosition;
    float fallingEdge = (1.0 - positionWithinCell) / (1.0 - peakPosition);
    float triangle = clamp(min(risingEdge, fallingEdge), 0.0, 1.0);
    float height = mix(0.2, 1.0, randomValue(cellIdentifier + seed + 17.0));
    float activation = smoothstep(0.45, 0.7, randomValue(cellIdentifier + seed + 41.0));
    return pow(triangle, 6.0) * height * activation;
}

void main() {
    vec2 positionFromStartPixels = fragmentPixelHomogeneous.xy / fragmentPixelHomogeneous.z - beamStartPixels;
    float positionAlongBeamPixels = clamp(dot(positionFromStartPixels, beamDirectionPixels), 0.0, beamLengthPixels);
    float positionPixels = (positionAlongBeamPixels + animationTimeSeconds * 72.0 * lightningAnimationFrequency) * lightningSpikeDensity;
    vec2 perpendicularPixels = vec2(-beamDirectionPixels.y, beamDirectionPixels.x);
    float signedDistanceFromCenter = dot(positionFromStartPixels, perpendicularPixels);
    float sideSeed = signedDistanceFromCenter < 0.0 ? 37.0 : 83.0;
    float lightningVisibility = clamp(lightningIntensity, 0.0, 1.0);
    float largeSpike = spike(positionPixels, 24.0, sideSeed);
    float smallSpike = spike(positionPixels, 11.0, sideSeed + 113.0);
    float spikeWidth = max(largeSpike, smallSpike * 0.45) * spikeReachPixels;
    float edgePosition = coreHalfWidthPixels + lightningVisibility + spikeWidth;
    float distanceFromCenter = abs(signedDistanceFromCenter);
    float coreCoverage = 1.0 - smoothstep(coreHalfWidthPixels - 0.75, coreHalfWidthPixels + 0.75, distanceFromCenter);
    float edgeCoverage = 1.0 - smoothstep(edgePosition - 1.0, edgePosition + 0.5, distanceFromCenter);
    float fringeFade = 1.0 - smoothstep(coreHalfWidthPixels, max(edgePosition, coreHalfWidthPixels + 0.001), distanceFromCenter);
    float coverage = max(coreCoverage, edgeCoverage * fringeFade * 0.7 * lightningVisibility);
    result = vec4(beamColor.rgb, beamColor.a * coverage);
}
