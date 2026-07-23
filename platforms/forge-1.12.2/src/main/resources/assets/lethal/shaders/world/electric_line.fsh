#version 120

uniform float coreHalfWidthPixels;
uniform float spikeReachPixels;
uniform float lightningIntensity;
uniform float lightningSpikeDensity;
uniform float lightningAnimationFrequency;
uniform float animationTimeSeconds;

varying vec2 beamStartPixels;
varying vec2 beamDirectionPixels;
varying float beamLengthPixels;
varying vec4 beamColor;

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
    vec2 positionFromStartPixels = gl_FragCoord.xy - beamStartPixels;
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
    gl_FragColor = vec4(beamColor.rgb, beamColor.a * coverage);
}
