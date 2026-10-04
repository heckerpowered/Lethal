#version 450

layout (location = 0) in vec2 lineCoordinates;

layout (std140, set = 0, binding = 0) uniform ElectricLineScene {
    mat4 modelViewProjectionMatrix;
    vec2 viewportSize;
};

layout (std140, set = 1, binding = 0) uniform ElectricLineStyle {
    float coreHalfWidthPixels;
    float ribbonHalfWidthPixels;
    float spikeReachPixels;
    float lightningIntensity;
    float lightningSpikeDensity;
    float lightningAnimationFrequency;
};

layout (push_constant) uniform ElectricLineDraw {
    layout (offset = 0) vec3 startPosition;
    layout (offset = 16) vec3 endPosition;
    layout (offset = 32) vec4 lineColor;
};

layout (location = 0) out vec2 beamStartPixels;
layout (location = 1) out vec2 beamDirectionPixels;
layout (location = 2) out float beamLengthPixels;
layout (location = 3) out vec4 beamColor;
layout (location = 4) out vec3 fragmentPixelHomogeneous;

void main() {
    float positionAlongLine = lineCoordinates.x;
    float side = lineCoordinates.y;
    vec4 startClipPosition = modelViewProjectionMatrix * vec4(startPosition, 1.0);
    vec4 endClipPosition = modelViewProjectionMatrix * vec4(endPosition, 1.0);
    vec2 startScreenPosition = startClipPosition.xy / startClipPosition.w;
    vec2 endScreenPosition = endClipPosition.xy / endClipPosition.w;
    // Pixel math retains the legacy bottom-left orientation; the RHI clip Y points downward.
    startScreenPosition.y = -startScreenPosition.y;
    endScreenPosition.y = -endScreenPosition.y;
    vec2 directionPixels = (endScreenPosition - startScreenPosition) * viewportSize * 0.5;
    float directionLengthPixels = max(length(directionPixels), 0.001);
    vec2 normalizedDirectionPixels = directionPixels / directionLengthPixels;
    vec2 perpendicularPixels = vec2(-normalizedDirectionPixels.y, normalizedDirectionPixels.x);
    vec2 offsetScreenPosition = perpendicularPixels * side * ribbonHalfWidthPixels * 2.0 / viewportSize;
    vec4 centerClipPosition = mix(startClipPosition, endClipPosition, positionAlongLine);
    offsetScreenPosition.y = -offsetScreenPosition.y;
    centerClipPosition.xy += offsetScreenPosition * centerClipPosition.w;

    gl_Position = centerClipPosition;
    // Homogeneous pixel coordinates preserve clipping and perspective interpolation.
    // The division happens in the fragment stage; this is the legacy bottom-left pixel space.
    fragmentPixelHomogeneous = vec3(
    (centerClipPosition.x + centerClipPosition.w) * viewportSize.x * 0.5,
    (-centerClipPosition.y + centerClipPosition.w) * viewportSize.y * 0.5,
    centerClipPosition.w
    );
    beamStartPixels = (startScreenPosition * 0.5 + 0.5) * viewportSize;
    beamDirectionPixels = normalizedDirectionPixels;
    beamLengthPixels = directionLengthPixels;
    beamColor = lineColor;
}
