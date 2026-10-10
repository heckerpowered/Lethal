#version 450

layout(location = 0) in vec3 surfacePoint;
layout(location = 1) in vec3 localPosition;
layout(location = 2) in vec4 lifeState;
layout(location = 0) out vec4 result;

layout(push_constant) uniform Explosion {
    mat4 clipFromLocal;
    vec4 timing;
    vec4 tint;
    vec4 cameraLocal;
} explosion;

const float PI = 3.14159265359;

float hashPoint(vec3 point) {
    point = fract(point * 0.1031);
    point += dot(point, point.yzx + 33.33);
    return fract((point.x + point.y) * point.z);
}

float fieldNoise(vec3 point) {
    vec3 cell = floor(point);
    vec3 fraction = fract(point);
    vec3 weight = fraction * fraction * (3.0 - 2.0 * fraction);
    float lower = mix(mix(hashPoint(cell), hashPoint(cell + vec3(1, 0, 0)), weight.x),
        mix(hashPoint(cell + vec3(0, 1, 0)), hashPoint(cell + vec3(1, 1, 0)), weight.x), weight.y);
    float upper = mix(mix(hashPoint(cell + vec3(0, 0, 1)), hashPoint(cell + vec3(1, 0, 1)), weight.x),
        mix(hashPoint(cell + vec3(0, 1, 1)), hashPoint(cell + vec3(1, 1, 1)), weight.x), weight.y);
    return mix(lower, upper, weight.z);
}

vec3 blueRadiance(float peak) {
    return vec3(0.0, 0.0, 1.0) * peak;
}

vec4 brightCore(float ageSeconds, vec3 point, vec3 viewDirection) {
    if (ageSeconds >= 0.105) return vec4(0.0);
    float turbulence = fieldNoise(point * 9.0 + vec3(explosion.timing.z, -ageSeconds * 5.0, 0.0));
    float facing = clamp(abs(dot(normalize(point), viewDirection)), 0.0, 1.0);
    float coverage = smoothstep(0.06, 0.12, facing);
    float cooling = 1.0 - smoothstep(0.018, 0.105, ageSeconds);
    float hotCore = smoothstep(0.975, 0.998, facing)
        * smoothstep(0.54, 0.78, turbulence) * cooling;
    vec3 radiance = blueRadiance(1.6) * (0.55 + turbulence * 0.45)
        + blueRadiance(1600.0) * hotCore;
    return vec4(radiance * coverage * cooling, coverage * cooling);
}

float triangleWave(float value) {
    return abs(fract(value) - 0.5) * 4.0 - 1.0;
}

float segmentLife(float ageSeconds, float segment, float phase, float firstEnd, float endSpread) {
    float random = hashPoint(vec3(floor(segment), phase, 7.0));
    float endsAt = firstEnd + random * endSpread;
    return clamp(ageSeconds / endsAt, 0.0, 1.0);
}

float shorteningSegment(float segment, float collapse, float initialHalfLength) {
    float pixelWidth = max(fwidth(segment), 0.001);
    if (collapse >= 1.0) return 0.0;
    float halfLength = initialHalfLength * (1.0 - collapse);
    float distance = abs(fract(segment) - 0.59);
    return 1.0 - smoothstep(halfLength, halfLength + pixelWidth, distance);
}

float narrowingLine(float distance, float initialHalfWidth, float collapse, float pixelWidth) {
    if (collapse >= 1.0) return 0.0;
    float halfWidth = initialHalfWidth * (1.0 - collapse);
    return 1.0 - smoothstep(halfWidth, halfWidth + pixelWidth, distance);
}

float narrowingArc(float distance, float initialHalfWidth, float life, float pixelWidth) {
    float halfWidth = initialHalfWidth * (1.0 - smoothstep(0.30, 1.0, life));
    return smoothstep(-pixelWidth, pixelWidth, halfWidth - distance)
        - smoothstep(-pixelWidth, pixelWidth, -halfWidth - distance);
}

vec4 electricalFork(float ageSeconds, vec3 point, vec3 forward, vec3 sideways, float phase) {
    vec3 normal = cross(forward, sideways);
    float angle = atan(dot(point, sideways), dot(point, forward));
    float lateral = dot(point, normal);
    float jagged = triangleWave(angle * 4.3 + phase) * 0.052
        + triangleWave(angle * 11.1 - phase) * 0.017;
    float mainDistance = abs(lateral - jagged);
    float branchOffset = smoothstep(-0.15, 1.1, angle) * 0.27;
    float branchDistance = abs(lateral - jagged - branchOffset);
    float pixelWidth = max(fwidth(lateral) * 1.1, 0.002);
    float segment = angle * 2.7 + phase;
    float mainLife = segmentLife(ageSeconds, segment, phase, 0.32, 0.28);
    float branchLife = segmentLife(ageSeconds, segment, phase + 2.3, 0.22, 0.23);
    float mainLine = narrowingArc(mainDistance, 0.003, mainLife, pixelWidth)
        * shorteningSegment(segment, 0.0, 0.41);
    float branchLine = narrowingArc(branchDistance, 0.002, branchLife, pixelWidth)
        * shorteningSegment(segment, 0.0, 0.41)
        * smoothstep(-0.15, 0.05, angle);
    float arcLength = smoothstep(-1.35, -1.1, angle) * (1.0 - smoothstep(0.9, 1.3, angle));
    vec3 light = blueRadiance(1.5) * mainLine
        + blueRadiance(1.0) * branchLine;
    return vec4(light * arcLength, (mainLine + branchLine * 0.65) * arcLength);
}

vec4 shortElectricalArcs(float ageSeconds, vec3 point) {
    float phase = explosion.timing.z;
    vec4 first = electricalFork(ageSeconds, point, vec3(1, 0, 0), vec3(0, 1, 0), phase);
    vec4 second = electricalFork(ageSeconds, point, vec3(0, 0, 1), vec3(1, 0, 0), phase + 1.7);
    vec4 third = electricalFork(ageSeconds, point, vec3(0, 1, 0), vec3(0, 0, 1), phase + 3.1);
    return (first + second + third) * smoothstep(0.01, 0.025, ageSeconds);
}

vec4 breakingShell(float ageSeconds, vec3 point, vec3 viewDirection) {
    float geometryAgeSeconds = min(ageSeconds, 0.18);
    vec3 drift = vec3(explosion.timing.z, geometryAgeSeconds * 0.65, -geometryAgeSeconds * 0.4);
    float field = fieldNoise(point * 5.5 + drift) * 0.7 + fieldNoise(point * 16.0 - drift) * 0.3;
    float ridgeDistance = abs(field - 0.52);
    float pixelWidth = max(fwidth(field) * 0.9, 0.0015);
    float patchRandom = hashPoint(vec3(floor(point.xy * 3.2), explosion.timing.z));
    float patchLife = clamp(ageSeconds / (0.36 + patchRandom * 0.23), 0.0, 1.0);
    float halfWidth = 0.012 * (1.0 - smoothstep(0.28, 1.0, patchLife) * 0.92);
    float veins = 1.0 - smoothstep(halfWidth, halfWidth + pixelWidth, ridgeDistance);
    float facing = clamp(abs(dot(normalize(point), viewDirection)), 0.0, 1.0);
    float rim = pow(1.0 - facing, 2.5);
    float fractureField = fieldNoise(point * 3.2 + vec3(explosion.timing.z, 1.0, 3.0));
    float erosion = 0.23 + 0.79 * smoothstep(0.15, 1.0, patchLife);
    float erosionEdge = max(fwidth(fractureField) * 0.75, 0.003);
    float breakup = smoothstep(erosion, erosion + erosionEdge, fractureField);
    float cloudField = fieldNoise(point * 3.2 + vec3(explosion.timing.z + 2.0, geometryAgeSeconds * 4.0, 5.0));
    float cloudLife = clamp(ageSeconds / (0.16 + patchRandom * 0.13), 0.0, 1.0);
    float cloudErosion = 0.25 + 0.82 * smoothstep(0.10, 1.0, cloudLife);
    float cloudEdge = max(fwidth(cloudField), 0.004);
    float cloud = smoothstep(cloudErosion, cloudErosion + max(cloudEdge, 0.12), cloudField);
    float coverage = veins * breakup;
    float cloudCoverage = cloud * (0.65 + cloudField * 0.35);
    vec3 radiance = blueRadiance(1.8)
        * (1.0 + veins * rim * 0.3);
    vec4 arcs = shortElectricalArcs(ageSeconds, normalize(point));
    vec3 cloudRadiance = blueRadiance(1.55)
        * (0.55 + cloudField * 0.45);
    vec3 light = cloudRadiance * cloudCoverage
        + radiance * coverage * (1.0 - cloudCoverage) + arcs.rgb * 0.55;
    // Sparse vein hotspots carry the local HDR peak; the surrounding shell stays blue.
    float junctionField = fieldNoise(point * 10.0 + drift + vec3(7.0, 3.0, 11.0));
    float junction = coverage * smoothstep(0.65, 0.79, junctionField)
        * (1.0 - cloudCoverage);
    float junctionLife = 1.0 - smoothstep(0.08, 0.22, ageSeconds);
    light += blueRadiance(900.0) * junction * junctionLife;
    float remaining = 1.0 - smoothstep(0.28, 0.62, ageSeconds);
    return vec4(light * remaining, clamp(max(max(coverage, arcs.a), cloudCoverage), 0.0, 1.0) * remaining);
}

vec4 shockRing(float ageSeconds, vec3 point) {
    float radius = length(point.xz);
    float angle = atan(point.z, point.x);
    float segment = angle * 36.0 / (2.0 * PI) + explosion.timing.z;
    float life = segmentLife(ageSeconds, segment, explosion.timing.z, 0.22, 0.27);
    float collapse = smoothstep(0.28, 1.0, life);
    float radialWidth = max(fwidth(radius), 0.0005);
    float band = narrowingLine(abs(radius - 1.0), 0.007, collapse, radialWidth);
    float piece = shorteningSegment(segment, collapse, 0.40);
    float coverage = band * piece;
    vec3 radiance = blueRadiance(1.8);
    return vec4(radiance * coverage, coverage);
}

vec4 outwardFragment(vec3 point) {
    if (point.z < 0.5 || lifeState.z <= 0.0 || lifeState.w <= 0.0) return vec4(0.0);
    float side = 1.0 - smoothstep(0.35, 0.95, abs(point.y));
    float head = smoothstep(0.0, 0.8, point.x);
    float coverage = side * (0.3 + head * 0.7);
    float brightness = 1.0 - smoothstep(0.0, 1.0, lifeState.x);
    float tip = smoothstep(0.88, 1.0, point.x)
        * (1.0 - smoothstep(0.12, 0.40, abs(point.y)));
    float spark = step(0.88, hashPoint(floor(normalize(localPosition) * 5.0)
        + vec3(explosion.timing.z))) * tip;
    float hotLife = 1.0 - smoothstep(0.04, 0.32, explosion.timing.x);
    vec3 radiance = blueRadiance(1.6) * (0.65 + head * 0.35) * brightness
        + vec3(0.0, 0.0, 8.0) * spark * hotLife;
    return vec4(radiance * coverage, coverage * brightness);
}

void main() {
    float ageSeconds = explosion.timing.x;
    if (ageSeconds < 0.0 || ageSeconds >= explosion.timing.y) discard;
    vec3 cameraOffset = explosion.cameraLocal.xyz - localPosition;
    vec3 viewDirection = cameraOffset / max(length(cameraOffset), 0.0001);
    // The engine mesh path is two-sided; select one physical hemisphere before shading.
    if (explosion.timing.w < 1.5 && dot(normalize(surfacePoint), viewDirection) * explosion.cameraLocal.w < 0.0) discard;
    vec4 energy;
    if (explosion.timing.w < 0.5) {
        energy = brightCore(ageSeconds, surfacePoint, viewDirection);
    } else if (explosion.timing.w < 1.5) {
        energy = breakingShell(ageSeconds, surfacePoint, viewDirection);
    } else if (explosion.timing.w < 2.5) {
        energy = shockRing(ageSeconds, surfacePoint);
    } else {
        energy = outwardFragment(surfacePoint);
    }
    if (energy.a < 0.0001) discard;
    float cameraFade = smoothstep(0.35, 3.0, length(explosion.cameraLocal.xyz));
    if (explosion.timing.w < 0.5) cameraFade *= smoothstep(1.5, 4.0, length(explosion.cameraLocal.xyz));
    float radianceScale = explosion.timing.w >= 2.5 ? 10.0 : (explosion.timing.w >= 0.5 ? 1.075 : 0.43);
    result = vec4(energy.rgb * explosion.tint.w * radianceScale * 1.1, clamp(energy.a, 0.0, 1.0) * cameraFade);
}
