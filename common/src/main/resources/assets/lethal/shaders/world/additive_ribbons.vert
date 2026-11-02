#version 450

layout (push_constant) uniform Explosion {
    mat4 clipFromLocal;
    vec4 timing;
    vec4 appearance;
    vec4 cameraLocal;
} explosion;

layout (location = 0) in vec3 coordinates;

layout (location = 0) out vec3 surfacePoint;
layout (location = 1) out vec3 localPosition;
layout (location = 2) out vec4 lifeState;

const float PI = 3.141592653589793;

struct Ribbon {
    vec3 position;
    vec3 tangent;
    float halfWidth;
    float visibility;
};

float pathRandom(float pathIndex) {
    return fract(sin(pathIndex * 17.13 + explosion.timing.z * 3.71) * 43758.5453);
}

float triangle(float phase) {
    return abs(fract(phase) - 0.5) * 4.0 - 1.0;
}

vec3 circlePoint(float pathIndex, float angle) {
    float yaw = pathIndex * 1.71 + explosion.timing.z * 0.37;
    vec3 right = vec3(cos(yaw), 0.0, sin(yaw));
    vec3 up = normalize(vec3(-sin(yaw) * 0.3, 1.0, cos(yaw) * 0.3));
    return right * cos(angle) + up * sin(angle);
}

vec3 mainArcPoint(float pathIndex, float pathFraction) {
    float angle = (pathRandom(pathIndex) * 2.0 - 1.0) * PI + (pathFraction - 0.5) * 1.12;
    vec3 radial = circlePoint(pathIndex, angle);
    vec3 sideways = circlePoint(pathIndex, angle + PI * 0.5);
    float jaggedOffset = (triangle(pathFraction * 7.0 + pathIndex) * 0.14 +
    triangle(pathFraction * 13.0 - pathIndex) * 0.055) * sin(PI * pathFraction);
    return radial * explosion.appearance.z + sideways * jaggedOffset;
}

vec3 arcPoint(float pathIndex, float pathFraction) {
    if (pathIndex < 4.5) return mainArcPoint(pathIndex, pathFraction);

    float branchIndex = pathIndex - 5.0;
    float parentIndex = floor(branchIndex * 0.5);
    float branchSide = mod(branchIndex, 2.0);
    float attachmentFraction = mix(0.31, 0.66, branchSide);
    vec3 root = mainArcPoint(parentIndex, attachmentFraction);
    vec3 tangent = normalize(mainArcPoint(parentIndex, attachmentFraction + 0.01) -
                             mainArcPoint(parentIndex, attachmentFraction - 0.01));
    vec3 radial = normalize(root);
    vec3 direction = normalize(radial * 0.65 + tangent * mix(-0.7, 0.7, branchSide));
    vec3 normal = normalize(cross(radial, tangent));
    float jaggedOffset = triangle(pathFraction * 5.0 + pathIndex) * 0.10 * sin(PI * pathFraction);
    return root + direction * explosion.appearance.z * 0.31 * pathFraction + normal * jaggedOffset;
}

float flashVisibility(float ageSeconds, float startSeconds, float durationSeconds) {
    float progress = (ageSeconds - startSeconds) / durationSeconds;
    return smoothstep(0.0, 0.12, progress) * (1.0 - smoothstep(0.48, 1.0, progress));
}

Ribbon electricArc(float pathIndex, float pathFraction, float ageSeconds) {
    vec3 position = arcPoint(pathIndex, pathFraction);
    vec3 tangent = arcPoint(pathIndex, min(pathFraction + 0.01, 1.0)) -
    arcPoint(pathIndex, max(pathFraction - 0.01, 0.0));

    float parentIndex = pathIndex < 4.5 ? pathIndex : floor((pathIndex - 5.0) * 0.5);
    float flashOffsetSeconds = pathRandom(parentIndex + 8.0) * 0.013;
    float visibility = flashVisibility(ageSeconds, 0.17 + flashOffsetSeconds, 0.065) +
    0.65 * flashVisibility(ageSeconds, 0.29 + flashOffsetSeconds, 0.045);

    float halfWidth = pathIndex < 4.5 ? 0.045 : 0.018;
    return Ribbon(position, tangent, halfWidth, visibility);
}

Ribbon pressureWisp(float pathIndex, float pathFraction, float ageSeconds) {
    float angle = pathRandom(pathIndex + 15.0) * 2.0 * PI +
    (pathFraction - 0.5) * (0.50 + pathRandom(pathIndex + 2.0) * 0.30) + ageSeconds * 0.65;
    vec3 direction = circlePoint(pathIndex + 0.4, angle);
    float rippleOffset = sin(pathFraction * 14.0 + pathIndex * 3.0 - ageSeconds * 22.0) * 0.045;
    vec3 position = direction * (explosion.appearance.z + rippleOffset);
    vec3 tangent = circlePoint(pathIndex + 0.4, angle + PI * 0.5);
    float halfWidth = (0.075 + pathRandom(pathIndex) * 0.035) * sin(PI * pathFraction);
    return Ribbon(position, tangent, halfWidth, 1.0);
}

void main() {
    float ageSeconds = explosion.timing.x;
    float layer = explosion.timing.w;
    float pathIndex = coordinates.x;
    float pathFraction = coordinates.y;

    if (layer < 0.5) {
        surfacePoint = coordinates;
        localPosition = coordinates * explosion.appearance.z;
        lifeState = vec4(0.0, 0.0, 1.0, 1.0);
        gl_Position = explosion.clipFromLocal * vec4(localPosition, 1.0);
        return;
    }

    Ribbon ribbon;
    if (layer < 1.5) {
        ribbon = electricArc(pathIndex, pathFraction, ageSeconds);
    } else {
        ribbon = pressureWisp(pathIndex, pathFraction, ageSeconds);
    }

    vec3 sight = explosion.cameraLocal.xyz - ribbon.position;
    vec3 sideways = cross(ribbon.tangent, sight);
    sideways /= max(length(sideways), 0.0001);

    localPosition = ribbon.position + sideways * coordinates.z * ribbon.halfWidth;
    surfacePoint = vec3(pathFraction, coordinates.z, 1.0);
    lifeState = vec4(pathIndex, step(4.5, pathIndex), ribbon.visibility, ribbon.halfWidth);
    gl_Position = explosion.clipFromLocal * vec4(localPosition, 1.0);
}
