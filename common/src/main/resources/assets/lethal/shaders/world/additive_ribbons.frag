#version 450

layout (push_constant) uniform Explosion {
    mat4 clipFromLocal;
    vec4 timing;
    vec4 appearance;
    vec4 cameraLocal;
} explosion;

layout (location = 0) in vec3 surfacePoint;
layout (location = 1) in vec3 localPosition;
layout (location = 2) in vec4 lifeState;

layout (location = 0) out vec4 result;

const float overallBrightness = 0.096;

float segmentRandom(float segmentIndex) {
    return fract(sin(segmentIndex * 17.13 + explosion.timing.z * 3.71) * 43758.5453);
}

vec3 bodyRadiance() {
    vec3 color = vec3(0.2, 0.45, 1.0);
    return color * (dot(vec3(0.0, 0.1, 1000.0), vec3(0.2126, 0.7152, 0.0722)) /
    dot(color, vec3(0.2126, 0.7152, 0.0722)));
}

void main() {
    float layer = explosion.timing.w;
    float envelope = explosion.appearance.y;
    float coverage;
    vec3 radiance;

    if (layer < 0.5) {
        vec3 sight = normalize(explosion.cameraLocal.xyz - localPosition);
        float facing = dot(normalize(surfacePoint), sight) * explosion.cameraLocal.w;
        if (facing <= 0.0) discard;
        coverage = smoothstep(0.25, 0.7, facing) * envelope;

        float whiteHighlight = pow(max(facing, 0.0), 24.0);
        float luminance = dot(bodyRadiance(), vec3(0.2126, 0.7152, 0.0722));
        radiance = mix(bodyRadiance() * 0.8, vec3(luminance), whiteHighlight) * coverage;
    } else if (layer < 1.5) {
        float sideDistance = abs(surfacePoint.y);
        float widthCoverage = 1.0 - smoothstep(0.60, 1.0, sideDistance);
        float endCoverage = smoothstep(0.0, 0.05, surfacePoint.x) *
        (1.0 - smoothstep(0.94, 1.0, surfacePoint.x));
        float flashVisibility = lifeState.z;
        float branchWeight = mix(0.46, 0.29, lifeState.y);
        coverage = widthCoverage * endCoverage * flashVisibility * envelope * branchWeight;

        float whiteHighlight = pow(max(1.0 - sideDistance, 0.0), 6.0) * 0.45;
        float luminance = dot(bodyRadiance(), vec3(0.2126, 0.7152, 0.0722));
        radiance = mix(bodyRadiance(), vec3(luminance), whiteHighlight) * coverage;
    } else {
        float pathFraction = surfacePoint.x;
        float widthCoverage = 1.0 - smoothstep(0.2, 1.0, abs(surfacePoint.y));
        float segmentIndex = floor(pathFraction * 7.0);
        float pathIndex = lifeState.x;
        float segmentVisibility = step(0.32, segmentRandom(segmentIndex + pathIndex * 11.0));
        float endCoverage = smoothstep(0.0, 0.08, pathFraction) *
        (1.0 - smoothstep(0.82, 1.0, pathFraction));
        coverage = widthCoverage * segmentVisibility * endCoverage * envelope * 0.09;
        radiance = bodyRadiance() * 0.18 * coverage;
    }

    if (coverage < 0.0001) discard;

    result = vec4(radiance * explosion.appearance.w * overallBrightness, clamp(coverage * 0.04, 0.0, 1.0));
}
