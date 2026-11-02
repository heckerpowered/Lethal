#version 450

layout(push_constant) uniform Explosion {
    mat4 clipFromLocal;
    vec4 timing;
    vec4 appearance;
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

uint particleHash(uint seed, uint index, uint channel) {
    uint value = seed ^ ((index + 1u) * 0x9e3779b9u) ^ (channel * 0x85ebca6bu);
    value ^= value >> 16u;
    value *= 0x7feb352du;
    value ^= value >> 15u;
    value *= 0x846ca68bu;
    return value ^ (value >> 16u);
}

float particleSample(float fragment, uint channel) {
    uint seed = uint(floor(explosion.timing.z * 1024.0 + 0.5));
    return float(particleHash(seed, uint(fragment), channel) >> 8u) * (1.0 / 16777216.0);
}

vec3 jetParameters(float fragment) {
    float birthSeconds = .045 + fragmentRandom(fragment, explosion.timing.z + 4.7) * .020;
    float lifetimeSeconds = .22 + particleSample(fragment, 1u) * .18;
    float trajectorySpeed = 152.5 + particleSample(fragment, 2u) * 122.0;
    return vec3(birthSeconds, lifetimeSeconds, trajectorySpeed);
}

float jetAlive(vec3 particle, float ageSeconds) {
    return step(particle.x, ageSeconds) * (1.0 - step(particle.x + particle.y, ageSeconds));
}

// Vertex bounds and fragment shutter integration must evaluate the identical particle trajectory.
float fragmentGrowth(float elapsed) {
    float u = max(elapsed, 0.0) / .028;
    return .72 + .28 * (1.0 - (1.0 + 2.511512910526232 * u) * exp(-5.6687 * u));
}

float speedMotionTime(float elapsed, float secondRandom) {
    float burstSeconds = 0.028 + secondRandom * 0.010;
    float strength = explosion.shutter.z * 0.5;
    return elapsed + strength * burstSeconds * (1.0 - exp(-elapsed / burstSeconds));
}

float radialTravel(float elapsed, float fastDrag, float driftDrag, float initialSpeed, float secondRandom) {
    float time = speedMotionTime(max(elapsed, 0.0), secondRandom);
    return 0.65 + initialSpeed * (0.80 / fastDrag * (1.0 - exp(-fastDrag * time)) +
    0.20 / driftDrag * (1.0 - exp(-driftDrag * time)));
}

float coreEnergyScale;
float tailEnergyScale;
float tailPart;

vec3 fragmentPosition(float ageSeconds, vec2 corner) {
    float fragment = mod(fragmentIndex, 12.0);
    tailPart = step(12.0, fragmentIndex);
    float random = fragmentRandom(fragment, explosion.timing.z);
    float secondRandom = fragmentRandom(fragment, explosion.timing.z + 4.7);
    float jet = 1.0;
    vec3 particle = jetParameters(fragment);

    float bornAt = particle.x;
    float endsAt = bornAt + particle.y;
    float elapsed = max(ageSeconds - bornAt, 0.0);
    float life = (ageSeconds - bornAt) / (endsAt - bornAt);

    float fastDrag = 26.0 + secondRandom * 8.0;
    float driftDrag = 5.5 + secondRandom * 2.5;
    float initialSpeed = particle.z;
    float motionTime = speedMotionTime(elapsed, secondRandom);
    float burstSeconds = 0.028 + secondRandom * 0.010;
    float timeSpeed = 1.0 + explosion.shutter.z * 0.5 * exp(-elapsed / burstSeconds);
    float speedFraction =
    (exp(-fastDrag * motionTime) * 0.80 + exp(-driftDrag * motionTime) * 0.20) * timeSpeed;
    float speed = initialSpeed * speedFraction;
    float travel = radialTravel(elapsed, fastDrag, driftDrag, initialSpeed, secondRandom);

    float growth = fragmentGrowth(elapsed);
    float burnout = 1.0 - smoothstep(0.46, 1.0, life);
    float shardLength = (0.065 + speed * 0.0045) * (0.82 + 0.18 * growth) *
    (1.0 - smoothstep(0.72, 1.0, life)) * explosion.appearance.x;
    float shardWidth =
    (0.017 + random * 0.017) * growth * burnout * explosion.appearance.x * mix(.78, .58, jet);

    float tailLength =
    min(max(travel - radialTravel(elapsed - 0.006, fastDrag, driftDrag, initialSpeed, secondRandom), 0.0),
        0.45);
    tailLength *= (1.0 - smoothstep(0.45, 0.85, life)) * explosion.shutter.z;

    // These shape integrals allocate at most 10% of body light to the artistic tail.
    const float coreShapeIntegral = 0.484688;
    const float tailShapeIntegral = 0.081666667;
    tailEnergyScale =
    min(0.35, 0.10 * shardLength * coreShapeIntegral / max(tailLength * tailShapeIntegral, 0.000001));
    coreEnergyScale = 1.0 - tailEnergyScale * tailLength * tailShapeIntegral /
    max(shardLength * coreShapeIntegral, 0.000001);

    float elevation = 1.0 - 2.0 * (fragment + 0.5) / 12.0;
    float azimuth = fragment * 2.39996323 + explosion.timing.z * 0.37 + .83;
    float horizontal = sqrt(max(1.0 - elevation * elevation, 0.0));
    vec3 direction = vec3(horizontal * cos(azimuth), elevation, horizontal * sin(azimuth));
    vec3 sight = explosion.cameraLocal.xyz - direction * travel;
    vec3 sideways = cross(direction, sight);
    sideways /= max(length(sideways), 0.0001);

    float alive = jetAlive(particle, ageSeconds);
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

vec4 outwardFragment(vec3 point) {
    if (point.z < 0.5 || lifeState.z <= 0.0 || lifeState.w <= 0.0)

    return vec4(0.0);

    float side = 1.0 - smoothstep(0.35, 0.95, abs(point.y));
    float head = smoothstep(0.0, 0.8, point.x);
    float coverage = side * (0.3 + head * 0.7);
    float brightness = 1.0 - smoothstep(0.0, 1.0, lifeState.x);

    float tip = smoothstep(0.88, 1.0, point.x) * (1.0 - smoothstep(0.12, 0.40, abs(point.y)));
    float spark =
    step(0.88, hashPoint(floor(normalize(localPosition) * 5.0) + vec3(explosion.timing.z))) * tip;
    float hotLife = 1.0 - smoothstep(0.04, 0.32, sampleAgeSeconds);

    vec3 radiance = bodyRadiance() * (0.65 + head * 0.35) * brightness * coreEnergyScale +
    bodyRadiance() * spark * hotLife * .65;

    return vec4(radiance * coverage, coverage * brightness);
}

vec3 screenBarycentric(vec2 point, vec2 first, vec2 second, vec2 third) {
    vec2 along = second - first;
    vec2 across = third - first;
    vec2 relative = point - first;
    float denominator = along.x * across.y - along.y * across.x;
    if (abs(denominator) < 1e-12)
    return vec3(-1.0);

    float secondWeight = (relative.x * across.y - relative.y * across.x) / denominator;
    float thirdWeight = (along.x * relative.y - along.y * relative.x) / denominator;
    return vec3(1.0 - secondWeight - thirdWeight, secondWeight, thirdWeight);
}

void main() {
    vec4 accumulated = vec4(0.0);
    vec4 centerClip = explosion.clipFromLocal * vec4(0.0, 0.0, 0.0, 1.0);
    vec4 closerClip = explosion.clipFromLocal * vec4(explosion.cameraLocal.xyz * 0.5, 1.0);
    bool reversedDepth = closerClip.z / closerClip.w > centerClip.z / centerClip.w;
    float nearestDepth = reversedDepth ? 0.0 : 1.0;

    // Integrate native binary fragment coverage and premultiplied radiance together.
    for (int sampleIndex = 0; sampleIndex < 8; sampleIndex++) {
        if (float(sampleIndex) >= explosion.shutter.y)
        break;

        sampleAgeSeconds =
        explosion.timing.x - explosion.shutter.x * (float(sampleIndex) + 0.5) / explosion.shutter.y;
        if (sampleAgeSeconds < 0.0 || sampleAgeSeconds >= explosion.timing.y)
        continue;

        vec3 tailLeft = fragmentPosition(sampleAgeSeconds, vec2(0.0, -1.0));
        vec3 tailRight = fragmentPosition(sampleAgeSeconds, vec2(0.0, 1.0));
        vec3 headRight = fragmentPosition(sampleAgeSeconds, vec2(1.0, 1.0));
        vec3 headLeft = fragmentPosition(sampleAgeSeconds, vec2(1.0, -1.0));

        float alive = surfacePoint.z;
        vec4 firstClip = explosion.clipFromLocal * vec4(tailLeft, 1.0);
        vec4 secondClip = explosion.clipFromLocal * vec4(tailRight, 1.0);
        vec4 thirdClip = explosion.clipFromLocal * vec4(headRight, 1.0);
        vec4 fourthClip = explosion.clipFromLocal * vec4(headLeft, 1.0);
        if (min(min(firstClip.w, secondClip.w), min(thirdClip.w, fourthClip.w)) <= 0.001)
        continue;

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
        if (min(min(weights.x, weights.y), weights.z) < 0.0)
        continue;

        weights /= vec3(firstClip.w, secondClip.w, thirdClip.w);
        weights /= weights.x + weights.y + weights.z;
        localPosition = tailLeft * weights.x + secondPosition * weights.y + thirdPosition * weights.z;
        vec2 surface = vec2(0.0, -1.0) * weights.x + secondSurface * weights.y + thirdSurface * weights.z;
        surfacePoint = vec3(surface, alive);

        vec4 energy;
        if (tailPart > 0.5) {
            if (surfacePoint.z < 0.5 || lifeState.z <= 0.0 || lifeState.w <= 0.0)
            continue;
            float side = 1.0 - smoothstep(0.35, 0.95, abs(surfacePoint.y));
            float bodyLife = 1.0 - smoothstep(0.0, 1.0, lifeState.x);
            float coverage = side * surfacePoint.x * surfacePoint.x;
            energy = vec4(bodyRadiance() * bodyLife * coverage * tailEnergyScale,
            coverage * bodyLife * tailEnergyScale);
        } else {
            energy = outwardFragment(surfacePoint);
        }

        float cameraFade = smoothstep(0.35, 3.0, length(explosion.cameraLocal.xyz));
        if (energy.a < 0.0001 || clamp(energy.a, 0.0, 1.0) * cameraFade < 0.0001)
        continue;

        float coverageAlpha = tailPart > 0.5 ? energy.a : 1.0;
        accumulated += vec4(energy.rgb * explosion.appearance.w * overallBrightness, coverageAlpha) /
        explosion.shutter.y;

        vec4 clip = explosion.clipFromLocal * vec4(localPosition, 1.0);
        nearestDepth =
        reversedDepth ? max(nearestDepth, clip.z / clip.w) : min(nearestDepth, clip.z / clip.w);
    }

    if (accumulated.a <= 0.0)
    discard;

    result = accumulated;
    // One current-scene test approximates shutter visibility in the active projection.
    gl_FragDepth = clamp(nearestDepth, 0.0, 1.0);
    result.rgb *= .28;
    result.a *= .04;
}
