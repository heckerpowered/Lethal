#version 450
layout(push_constant) uniform Explosion {
    mat4 clipFromLocal;
    vec4 timing;
    vec4 tint;
    vec4 cameraLocal;
    vec4 shutter;
} explosion;
layout(location = 0) in vec2 screenPosition;
layout(location = 1) flat in float fragmentIndex;
layout(location = 0) out vec4 result;
vec3 surfacePoint;
vec3 localPosition;
vec4 lifeState;
float sampleAgeSeconds;
float fragmentRandom(float fragment, float phase) {
    return fract(sin(fragment * 17.13 + phase * 3.71) * 43758.5453);
}

// Matrix ElasticEase OUT, with one local size oscillation rather than radial reversal.
float elasticSize(float life) {
    float time = clamp(life / 0.34, 0.0, 1.0);
    float remaining = 1.0 - time;
    float exponent = (exp(3.0 * remaining) - 1.0) / (exp(3.0) - 1.0);
    return 1.0 - exponent * sin(7.85398163 * remaining);
}

float speedMotionTime(float elapsed, float secondRandom) {
    float burstSeconds = 0.028 + secondRandom * 0.010;
    float strength = explosion.shutter.z * 0.5;
    return elapsed + strength * burstSeconds * (1.0 - exp(-elapsed / burstSeconds));
}

float radialTravel(float elapsed, float fastDrag, float driftDrag, float initialSpeed, float secondRandom) {
    float time = speedMotionTime(max(elapsed, 0.0), secondRandom);
    return 0.65 + initialSpeed * (0.80 / fastDrag * (1.0 - exp(-fastDrag * time))
        + 0.20 / driftDrag * (1.0 - exp(-driftDrag * time)));
}

float coreEnergyScale;
float tailEnergyScale;
float tailPart;

vec3 fragmentPosition(float ageSeconds, vec2 corner) {
    float fragment = mod(fragmentIndex, 48.0);
    tailPart = step(48.0, fragmentIndex);
    float random = fragmentRandom(fragment, explosion.timing.z);
    float secondRandom = fragmentRandom(fragment, explosion.timing.z + 4.7);
    float bornAt = secondRandom * 0.015;
    float endsAt = (0.72 + random * 0.30) * mix(1.0, 0.88, explosion.shutter.z);
    float elapsed = max(ageSeconds - bornAt, 0.0);
    float life = (ageSeconds - bornAt) / (endsAt - bornAt);
    float fastDrag = 26.0 + secondRandom * 8.0;
    float driftDrag = 5.5 + secondRandom * 2.5;
    float initialSpeed = 125.0 + random * 100.0;
    float motionTime = speedMotionTime(elapsed, secondRandom);
    float burstSeconds = 0.028 + secondRandom * 0.010;
    float timeSpeed = 1.0 + explosion.shutter.z * 0.5 * exp(-elapsed / burstSeconds);
    float speedFraction = (exp(-fastDrag * motionTime) * 0.80 + exp(-driftDrag * motionTime) * 0.20) * timeSpeed;
    float speed = initialSpeed * speedFraction;
    float travel = radialTravel(elapsed, fastDrag, driftDrag, initialSpeed, secondRandom);
    float growth = mix(0.32 + 0.68 * elasticSize(life), 0.72 + 0.28 * (1.0 - exp(-elapsed * 100.0)), explosion.shutter.z);
    float burnout = 1.0 - smoothstep(0.46, 1.0, life);
    float shardLength = (0.065 + speed * 0.0045) * (0.82 + 0.18 * growth)
        * (1.0 - smoothstep(0.72, 1.0, life)) * explosion.tint.x;
    float shardWidth = (0.017 + random * 0.017) * growth * burnout * explosion.tint.x;
    float tailLength = min(max(travel - radialTravel(elapsed - 0.006, fastDrag, driftDrag, initialSpeed, secondRandom), 0.0), 0.45);
    tailLength *= (1.0 - smoothstep(0.45, 0.85, life)) * explosion.shutter.z;
    // These shape integrals allocate at most 6% of body light to the artistic tail.
    const float coreShapeIntegral = 0.484688;
    const float tailShapeIntegral = 0.081666667;
    tailEnergyScale = min(0.35, 0.06 * shardLength * coreShapeIntegral / max(tailLength * tailShapeIntegral, 0.000001));
    coreEnergyScale = 1.0 - tailEnergyScale * tailLength * tailShapeIntegral / max(shardLength * coreShapeIntegral, 0.000001);
    float elevation = 1.0 - 2.0 * (fragment + 0.5) / 48.0;
    float azimuth = fragment * 2.39996323 + explosion.timing.z * 0.37;
    float horizontal = sqrt(max(1.0 - elevation * elevation, 0.0));
    vec3 direction = vec3(horizontal * cos(azimuth), elevation, horizontal * sin(azimuth));
    vec3 sight = explosion.cameraLocal.xyz - direction * travel;
    vec3 sideways = cross(direction, sight);
    sideways /= max(length(sideways), 0.0001);
    float alive = step(bornAt, ageSeconds) * (1.0 - step(endsAt, ageSeconds));
    float taper;
    float radialPosition;
    if (tailPart > 0.5) {
        taper = 0.08 + 0.22 * corner.x;
        radialPosition = travel - tailLength * (1.0 - corner.x);
        alive *= step(0.0001, tailLength);
        lifeState = vec4(life, speedFraction, tailLength, shardWidth);
    } else {
        taper = 0.3 + 0.7 * corner.x;
        radialPosition = travel + corner.x * shardLength;
        lifeState = vec4(life, speedFraction, shardLength, shardWidth);
    }
    surfacePoint = vec3(corner.x, corner.y, alive);
    return direction * radialPosition + sideways * corner.y * shardWidth * taper;
}


const float PI = 3.14159265359;
const float overallBrightness = 0.096;

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

vec3 blueRadiance() {
    return vec3(0.0, 0.1, 1000.0);
}

vec3 bodyRadiance() {
    const vec3 paleBlue = vec3(0.2, 0.45, 1.0);
    const vec3 luminanceWeights = vec3(0.2126, 0.7152, 0.0722);
    // Match the previous linear luminance while moving energy from blue into red and green.
    float baselineLuminance = dot(blueRadiance(), luminanceWeights);
    return paleBlue * (baselineLuminance / dot(paleBlue, luminanceWeights));
}

vec4 brightCore(float ageSeconds, vec3 point, vec3 viewDirection) {
    if (ageSeconds >= 0.105) return vec4(0.0);
    float turbulence = fieldNoise(point * 9.0 + vec3(explosion.timing.z, -ageSeconds * 5.0, 0.0));
    float facing = clamp(abs(dot(normalize(point), viewDirection)), 0.0, 1.0);
    float coverage = smoothstep(0.06, 0.12, facing);
    float cooling = 1.0 - smoothstep(0.018, 0.105, ageSeconds);
    float whiteHot = smoothstep(0.975, 0.998, facing)
        * smoothstep(0.54, 0.78, turbulence) * cooling;
    vec3 radiance = bodyRadiance() * (0.55 + turbulence * 0.45)
        + bodyRadiance() * whiteHot;
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
    vec3 light = bodyRadiance() * mainLine
        + bodyRadiance() * branchLine;
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
    vec3 radiance = bodyRadiance()
        * (1.0 + veins * rim * 0.3);
    vec4 arcs = shortElectricalArcs(ageSeconds, normalize(point));
    vec3 cloudRadiance = bodyRadiance()
        * (0.55 + cloudField * 0.45);
    vec3 light = cloudRadiance * cloudCoverage
        + radiance * coverage * (1.0 - cloudCoverage) + arcs.rgb * 0.55;
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
    vec3 radiance = bodyRadiance();
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
    float hotLife = 1.0 - smoothstep(0.04, 0.32, sampleAgeSeconds);
    vec3 radiance = bodyRadiance() * (0.65 + head * 0.35) * brightness * coreEnergyScale
        + bodyRadiance() * spark * hotLife;
    return vec4(radiance * coverage, coverage * brightness);
}


vec3 screenBarycentric(vec2 point, vec2 first, vec2 second, vec2 third) {
    vec2 along = second - first;
    vec2 across = third - first;
    vec2 relative = point - first;
    float denominator = along.x * across.y - along.y * across.x;
    if (abs(denominator) < 1e-12) return vec3(-1.0);
    float secondWeight = (relative.x * across.y - relative.y * across.x) / denominator;
    float thirdWeight = (along.x * relative.y - along.y * relative.x) / denominator;
    return vec3(1.0 - secondWeight - thirdWeight, secondWeight, thirdWeight);
}

void main() {
    vec4 accumulated = vec4(0.0);
    float nearestDepth = 0.0;
    // Integrate native binary fragment coverage and premultiplied radiance together.
    for (int sampleIndex = 0; sampleIndex < 8; sampleIndex++) {
        if (float(sampleIndex) >= explosion.shutter.y) break;
        sampleAgeSeconds = explosion.timing.x - explosion.shutter.x * (float(sampleIndex) + 0.5) / explosion.shutter.y;
        if (sampleAgeSeconds < 0.0 || sampleAgeSeconds >= explosion.timing.y) continue;
        vec3 tailLeft = fragmentPosition(sampleAgeSeconds, vec2(0.0, -1.0));
        vec3 tailRight = fragmentPosition(sampleAgeSeconds, vec2(0.0, 1.0));
        vec3 headRight = fragmentPosition(sampleAgeSeconds, vec2(1.0, 1.0));
        vec3 headLeft = fragmentPosition(sampleAgeSeconds, vec2(1.0, -1.0));
        float alive = surfacePoint.z;
        vec4 firstClip = explosion.clipFromLocal * vec4(tailLeft, 1.0);
        vec4 secondClip = explosion.clipFromLocal * vec4(tailRight, 1.0);
        vec4 thirdClip = explosion.clipFromLocal * vec4(headRight, 1.0);
        vec4 fourthClip = explosion.clipFromLocal * vec4(headLeft, 1.0);
        if (min(min(firstClip.w, secondClip.w), min(thirdClip.w, fourthClip.w)) <= 0.001) continue;
        vec3 weights = screenBarycentric(screenPosition, firstClip.xy / firstClip.w,
            secondClip.xy / secondClip.w, thirdClip.xy / thirdClip.w);
        vec3 secondPosition = tailRight;
        vec3 thirdPosition = headRight;
        vec2 secondSurface = vec2(0.0, 1.0);
        vec2 thirdSurface = vec2(1.0, 1.0);
        if (min(min(weights.x, weights.y), weights.z) < 0.0) {
            weights = screenBarycentric(screenPosition, firstClip.xy / firstClip.w,
                thirdClip.xy / thirdClip.w, fourthClip.xy / fourthClip.w);
            secondClip = thirdClip;
            thirdClip = fourthClip;
            secondPosition = headRight;
            thirdPosition = headLeft;
            secondSurface = vec2(1.0, 1.0);
            thirdSurface = vec2(1.0, -1.0);
        }
        if (min(min(weights.x, weights.y), weights.z) < 0.0) continue;
        weights /= vec3(firstClip.w, secondClip.w, thirdClip.w);
        weights /= weights.x + weights.y + weights.z;
        localPosition = tailLeft * weights.x + secondPosition * weights.y + thirdPosition * weights.z;
        vec2 surface = vec2(0.0, -1.0) * weights.x + secondSurface * weights.y + thirdSurface * weights.z;
        surfacePoint = vec3(surface, alive);
        vec4 energy;
        if (tailPart > 0.5) {
            if (surfacePoint.z < 0.5 || lifeState.z <= 0.0 || lifeState.w <= 0.0) continue;
            float side = 1.0 - smoothstep(0.35, 0.95, abs(surfacePoint.y));
            float bodyLife = 1.0 - smoothstep(0.0, 1.0, lifeState.x);
            float coverage = side * surfacePoint.x * surfacePoint.x;
            energy = vec4(bodyRadiance() * bodyLife * coverage * tailEnergyScale,
                coverage * bodyLife * tailEnergyScale);
        } else {
            energy = outwardFragment(surfacePoint);
        }
        float cameraFade = smoothstep(0.35, 3.0, length(explosion.cameraLocal.xyz));
        if (energy.a < 0.0001 || clamp(energy.a, 0.0, 1.0) * cameraFade < 0.0001) continue;
        float coverageAlpha = tailPart > 0.5 ? energy.a : 1.0;
        accumulated += vec4(energy.rgb * explosion.tint.w * overallBrightness, coverageAlpha) / explosion.shutter.y;
        vec4 clip = explosion.clipFromLocal * vec4(localPosition, 1.0);
        nearestDepth = max(nearestDepth, clip.z / clip.w);
    }
    if (accumulated.a <= 0.0) discard;
    result = accumulated;
    // 26.2 uses reversed depth. One current-scene test approximates shutter visibility.
    gl_FragDepth = clamp(nearestDepth, 0.0, 1.0);
}
