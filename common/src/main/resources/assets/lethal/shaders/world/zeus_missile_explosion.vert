#version 450

layout(location = 0) in vec3 coordinates;
layout(location = 0) out vec3 surfacePoint;
layout(location = 1) out vec3 localPosition;
layout(location = 2) out vec4 lifeState;

layout(push_constant) uniform Explosion {
    mat4 clipFromLocal;
    vec4 timing;
    vec4 tint;
    vec4 cameraLocal;
} explosion;

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

vec3 fragmentPosition(float ageSeconds) {
    float fragment = coordinates.x;
    float random = fragmentRandom(fragment, explosion.timing.z);
    float secondRandom = fragmentRandom(fragment, explosion.timing.z + 4.7);
    float bornAt = secondRandom * 0.015;
    float endsAt = 0.72 + random * 0.30;
    float elapsed = max(ageSeconds - bornAt, 0.0);
    float life = (ageSeconds - bornAt) / (endsAt - bornAt);
    float fastDrag = 26.0 + secondRandom * 8.0;
    float driftDrag = 5.5 + secondRandom * 2.5;
    float initialSpeed = 125.0 + random * 100.0;
    float impulse = exp(-fastDrag * elapsed);
    float drift = exp(-driftDrag * elapsed);
    float speedFraction = impulse * 0.80 + drift * 0.20;
    float speed = initialSpeed * speedFraction;
    // Integrated impulse and drift velocities keep the initial burst fast and the tail moving outward.
    float travel = 0.65 + initialSpeed * (0.80 / fastDrag * (1.0 - impulse)
        + 0.20 / driftDrag * (1.0 - drift));
    float growth = 0.32 + 0.68 * elasticSize(life);
    float burnout = 1.0 - smoothstep(0.46, 1.0, life);
    float shardLength = (0.065 + speed * 0.0045) * (0.82 + 0.18 * growth)
        * (1.0 - smoothstep(0.72, 1.0, life));
    float shardWidth = (0.017 + random * 0.017) * growth * burnout;
    float elevation = 1.0 - 2.0 * (fragment + 0.5) / 48.0;
    float azimuth = fragment * 2.39996323 + explosion.timing.z * 0.37;
    float horizontal = sqrt(max(1.0 - elevation * elevation, 0.0));
    vec3 direction = vec3(horizontal * cos(azimuth), elevation, horizontal * sin(azimuth));
    vec3 sight = explosion.cameraLocal.xyz - direction * travel;
    vec3 sideways = cross(direction, sight);
    sideways /= max(length(sideways), 0.0001);
    float taper = 0.3 + 0.7 * coordinates.y;
    float alive = step(bornAt, ageSeconds) * (1.0 - step(endsAt, ageSeconds));
    surfacePoint = vec3(coordinates.y, coordinates.z, alive);
    lifeState = vec4(life, speedFraction, shardLength, shardWidth);
    return direction * (travel + coordinates.y * shardLength) + sideways * coordinates.z * shardWidth * taper;
}

void main() {
    float ageSeconds = max(explosion.timing.x, 0.0);
    float layer = explosion.timing.w;
    float radius;
    vec3 shapedPosition = coordinates;
    if (layer < 0.5) {
        float rise = 1.0 - exp(-ageSeconds * 125.0);
        float collapse = 1.0 - smoothstep(0.035, 0.105, ageSeconds);
        radius = (0.18 + 2.4 * rise * exp(-ageSeconds * 20.0)) * collapse;
        float corrugation = sin(coordinates.x * 6.5 + explosion.timing.z)
            * sin(coordinates.y * 5.8 - ageSeconds * 35.0) * sin(coordinates.z * 6.2 + ageSeconds * 20.0);
        shapedPosition *= 1.0 + 0.30 * corrugation;
        lifeState = vec4(ageSeconds / 0.105, exp(-ageSeconds * 125.0), radius, collapse);
    } else if (layer < 1.5) {
        float geometryAgeSeconds = min(ageSeconds, 0.18);
        radius = 0.55 + 6.6 * (1.0 - exp(-geometryAgeSeconds * 18.0));
        float corrugation = sin(coordinates.x * 5.5 + explosion.timing.z)
            * sin(coordinates.y * 4.5 - geometryAgeSeconds * 12.0) * sin(coordinates.z * 5.0 + geometryAgeSeconds * 14.0);
        shapedPosition *= 1.0 + 0.18 * corrugation;
        lifeState = vec4(ageSeconds / 0.61, exp(-ageSeconds * 18.0), radius, 1.0);
    } else if (layer < 2.5) {
        radius = 0.75 + 7.8 * (1.0 - exp(-ageSeconds * 23.0));
        float tilt = 0.27;
        shapedPosition = vec3(coordinates.x, coordinates.y * cos(tilt) - coordinates.z * sin(tilt),
            coordinates.y * sin(tilt) + coordinates.z * cos(tilt));
        lifeState = vec4(ageSeconds / 0.49, exp(-ageSeconds * 23.0), radius, 1.0);
    } else {
        localPosition = fragmentPosition(ageSeconds);
        gl_Position = explosion.clipFromLocal * vec4(localPosition, 1.0);
        return;
    }
    localPosition = shapedPosition * radius;
    surfacePoint = coordinates;
    gl_Position = explosion.clipFromLocal * vec4(localPosition, 1.0);
}
