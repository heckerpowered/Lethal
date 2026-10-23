#version 450
layout(location = 0) in vec3 coordinates;
layout(location = 0) out vec2 surfacePoint;
layout(location = 1) out vec4 sparkState;
layout(push_constant) uniform Element {
    mat4 clipFromLocal;
    vec4 timing;
    vec4 appearance;
    vec4 cameraLocal;
} element;

uint stableSparkHash(uint seed, uint index, uint channel) {
    uint value = seed ^ ((index + 1u) * 0x9e3779b9u) ^ (channel * 0x85ebca6bu);
    value ^= value >> 16u;
    value *= 0x7feb352du;
    value ^= value >> 15u;
    value *= 0x846ca68bu;
    return value ^ (value >> 16u);
}

float stableSparkSample(float index, uint channel) {
    uint seed = uint(floor(element.timing.z * 1024.0 + .5));
    return float(stableSparkHash(seed, uint(index), channel) >> 8u) * (1.0 / 16777216.0);
}

// Birth, duration and initial speed are fixed for this seed/index; no age participates in sampling.
vec3 stableSparkParameters(float index) {
    return vec3(.008 + .047 * stableSparkSample(index, 1u),
                .28 + .26 * stableSparkSample(index, 2u),
                95.0 + 65.0 * stableSparkSample(index, 3u));
}

void main() {
    float index = coordinates.x;
    float along = coordinates.y;
    float side = coordinates.z;
    vec3 particle = stableSparkParameters(index);
    float age = element.timing.x;
    float elapsed = max(age - particle.x, 0.0);
    float life = elapsed / particle.y;
    float alive = step(particle.x, age) * (1.0 - step(particle.x + particle.y, age));
    alive *= 1.0 - step(min(element.timing.y, .72), age);
    float drag = 13.0 + 7.0 * stableSparkSample(index, 4u);
    float travel = .30 + particle.z / drag * (1.0 - exp(-drag * elapsed));
    float trailLength = (1.4 + 1.4 * stableSparkSample(index, 5u)) * (1.0 - .30 * clamp(life, 0.0, 1.0));
    trailLength = min(trailLength, max(travel - .25, 0.0));
    float elevation = 1.0 - 2.0 * (index + .5) / 24.0;
    float azimuth = index * 2.39996323 + element.timing.z * .37 + .83;
    float horizontal = sqrt(max(1.0 - elevation * elevation, 0.0));
    vec3 direction = vec3(horizontal * cos(azimuth), elevation, horizontal * sin(azimuth));
    vec3 head = direction * travel;
    vec3 sight = element.cameraLocal.xyz - head;
    vec3 sideways = cross(direction, sight);
    if (length(sideways) < .0001) sideways = cross(direction, vec3(0.0, 1.0, 0.0));
    if (length(sideways) < .0001) sideways = cross(direction, vec3(1.0, 0.0, 0.0));
    sideways = normalize(sideways);
    float width = .045 + .015 * stableSparkSample(index, 6u);
    float taper = .08 + .92 * pow(along, .85);
    vec3 position = direction * (travel - trailLength * (1.0 - along)) + sideways * side * width * taper;
    surfacePoint = vec2(along, side);
    sparkState = vec4(life, elapsed, stableSparkSample(index, 7u), alive);
    gl_Position = element.clipFromLocal * vec4(position, 1.0);
}
