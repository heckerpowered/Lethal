#version 450

layout(push_constant) uniform Explosion {
    mat4 clipFromLocal;
    vec4 timing;
    vec4 appearance;
    vec4 cameraLocal;
    vec4 shutter;
} explosion;

layout(location = 0) in vec3 coordinates;

layout(location = 0) out vec2 screenPosition;
layout(location = 1) flat out float fragmentIndex;

vec3 surfacePoint;
vec4 lifeState;

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

void main() {
    fragmentIndex = coordinates.x;
    vec2 lower = vec2(1e10);
    vec2 upper = vec2(-1e10);

    for (int sampleIndex = 0; sampleIndex < 8; sampleIndex++) {
        if (float(sampleIndex) >= explosion.shutter.y)
        break;

        float age =
        explosion.timing.x - explosion.shutter.x * (float(sampleIndex) + 0.5) / explosion.shutter.y;
        for (int cornerIndex = 0; cornerIndex < 4; cornerIndex++) {
            vec2 corner = vec2(float(cornerIndex / 2), float(cornerIndex % 2) * 2.0 - 1.0);
            vec4 clip = explosion.clipFromLocal * vec4(fragmentPosition(age, corner), 1.0);
            if (clip.w > 0.001) {
                lower = min(lower, clip.xy / clip.w);
                upper = max(upper, clip.xy / clip.w);
            }
        }
    }

    // No projected sample (or a wholly offscreen box) contributes any visible pixels.
    // Leaving the initial +/-1e10 bounds intact would rasterize a giant discard-only quad.
    if (any(greaterThanEqual(lower, upper)) || any(greaterThan(lower, vec2(1.0))) ||
    any(lessThan(upper, vec2(-1.0)))) {
        screenPosition = vec2(2.0);
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }

    screenPosition = mix(lower, upper, vec2(coordinates.y, (coordinates.z + 1.0) * 0.5));
    gl_Position = vec4(screenPosition, 0.5, 1.0);
}
