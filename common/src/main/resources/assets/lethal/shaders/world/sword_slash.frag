#version 450
layout(push_constant) uniform Slash { mat4 clipFromLocal; vec4 motion; vec4 baseColor; } effect;
layout(location = 0) in vec4 trailState;
layout(location = 1) in vec4 fragmentClip;
layout(location = 2) in vec2 trailArcState;
layout(set = 0, binding = 0) uniform sampler2D worldDepth;
layout(set = 1, binding = 0) uniform WorldDepthComparison { float worldDepthDirection; };
layout(location = 0) out vec4 result;

float hash(vec2 point) { return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 point) {
    vec2 cell = floor(point), fraction = fract(point);
    vec2 weight = fraction * fraction * (3.0 - 2.0 * fraction);
    return mix(mix(hash(cell), hash(cell + vec2(1, 0)), weight.x),
        mix(hash(cell + vec2(0, 1)), hash(cell + vec2(1, 1)), weight.x), weight.y);
}
void main() {
    vec3 ndc = fragmentClip.xyz / fragmentClip.w;
    vec2 depthUv = vec2(ndc.x * 0.5 + 0.5, 0.5 - ndc.y * 0.5);
    if ((ndc.z - texture(worldDepth, depthUv).r) * worldDepthDirection > 0.0) discard;
    float age = max(0.0, effect.motion.x - trailState.x);
    float style = effect.motion.y;
    float textureTime = trailArcState.y;
    float strength;
    float particleLifetimeSeconds = trailState.w;
    if (particleLifetimeSeconds > 0.0) {
        float localDistance = trailState.y * trailState.y + trailState.z * trailState.z;
        float particleBrightness = 1.0 - smoothstep(0.0, 1.0, age / particleLifetimeSeconds);
        strength = exp(-localDistance * 5.0) * 3.0 * particleBrightness;
    } else {
        float radial = trailState.y;
        float speed = trailState.z;
        float life = age / (style < 0.5 ? 0.24 : (style < 1.5 ? 0.21 : 0.30));
        float tail = 1.0 - smoothstep(0.72, 1.0, life);
        float detail = noise(vec2(textureTime * 92.0 - age * 6.0, radial * 11.0));
        float edgeDistance = abs(radial - 0.97);
        float edge = exp(-edgeDistance * edgeDistance / 0.000055);
        float body = pow(max(radial, 0.0), 1.2) * smoothstep(0.08, 0.32, radial);
        float fresh = exp(-age * 8.0);
        float erosion = smoothstep(life * 0.66 - 0.07, life * 0.66 + 0.1, detail + radial * 0.18);
        if (style < 0.5) {
            float brushed = pow(noise(vec2(textureTime * 22.0 - age * 2.0, radial * 64.0)), 2.4);
            strength = (edge * 8.5 + body * (0.10 + brushed * 1.24)) * erosion;
        } else if (style < 1.5) {
            float wakes = pow(0.5 + 0.5 * sin(radial * 63.0 + detail * 2.0 - textureTime * 12.0), 10.0);
            float sheath = exp(-pow((radial - 0.82) / 0.22, 2.0));
            strength = edge * 11.0 + sheath * (0.10 + detail * 0.18) + wakes * sheath * 0.24;
        } else {
            float core = exp(-pow((radial - 0.97) / 0.0055, 2.0));
            float sheath = exp(-pow((radial - 0.93) / 0.035, 2.0));
            float halo = exp(-pow((radial - 0.86) / 0.075, 2.0));
            float grain = 0.88 + detail * 0.12;
            strength = core * 4.8 + (sheath * 0.26 + halo * 0.08) * grain;
        }
        strength *= tail * (0.22 + speed * 1.5) * fresh;
    }
    result = vec4(effect.baseColor.rgb * strength * trailArcState.x, 0.0);
}
