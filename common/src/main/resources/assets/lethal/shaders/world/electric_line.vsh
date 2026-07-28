#version 120

attribute vec2 lineCoordinates;

uniform mat4 modelViewProjectionMatrix;
uniform vec2 viewportSize;
uniform float ribbonHalfWidthPixels;
uniform vec3 startPosition;
uniform vec3 endPosition;
uniform vec4 lineColor;

varying vec2 beamStartPixels;
varying vec2 beamDirectionPixels;
varying float beamLengthPixels;
varying vec4 beamColor;

void main() {
    float positionAlongLine = lineCoordinates.x;
    float side = lineCoordinates.y;
    vec4 startClipPosition = modelViewProjectionMatrix * vec4(startPosition, 1.0);
    vec4 endClipPosition = modelViewProjectionMatrix * vec4(endPosition, 1.0);
    vec2 startScreenPosition = startClipPosition.xy / startClipPosition.w;
    vec2 endScreenPosition = endClipPosition.xy / endClipPosition.w;
    vec2 directionPixels = (endScreenPosition - startScreenPosition) * viewportSize * 0.5;
    float directionLengthPixels = max(length(directionPixels), 0.001);
    vec2 normalizedDirectionPixels = directionPixels / directionLengthPixels;
    vec2 perpendicularPixels = vec2(-normalizedDirectionPixels.y, normalizedDirectionPixels.x);
    vec2 offsetScreenPosition = perpendicularPixels * side * ribbonHalfWidthPixels * 2.0 / viewportSize;
    vec4 centerClipPosition = mix(startClipPosition, endClipPosition, positionAlongLine);
    centerClipPosition.xy += offsetScreenPosition * centerClipPosition.w;

    gl_Position = centerClipPosition;
    beamStartPixels = (startScreenPosition * 0.5 + 0.5) * viewportSize;
    beamDirectionPixels = normalizedDirectionPixels;
    beamLengthPixels = directionLengthPixels;
    beamColor = lineColor;
}
