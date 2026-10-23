#version 450

layout(set = 0, binding = 0) uniform sampler2D background;
layout(set = 0, binding = 1) uniform sampler2D worldDepth;
layout(std140, set = 1, binding = 0) uniform AirShockwave {
    mat4 relativeToClip;
    mat4 clipToRelative;
    vec4 viewport; // width, height, count, conventional/reversed depth direction
    vec4 limits; // total pixel limit, absolute depth edge tolerance, relative depth edge tolerance
    vec4 center0;
    vec4 center1;
    vec4 center2;
    vec4 center3;
    vec4 center4;
    vec4 center5;
    vec4 center6;
    vec4 center7;
    vec4 wave0;
    vec4 wave1;
    vec4 wave2;
    vec4 wave3;
    vec4 wave4;
    vec4 wave5;
    vec4 wave6;
    vec4 wave7;
    // Fixed radius, pixel amplitude, temporal phase, spatial cycles.
    vec4 core0;
    vec4 core1;
    vec4 core2;
    vec4 core3;
    vec4 core4;
    vec4 core5;
    vec4 core6;
    vec4 core7;
} air;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;

// The current RHI OpenGL lowering accepts scalar block members but not block arrays.
vec4 centerAt(int index) {
    if (index == 0) return air.center0;
    if (index == 1) return air.center1;
    if (index == 2) return air.center2;
    if (index == 3) return air.center3;
    if (index == 4) return air.center4;
    if (index == 5) return air.center5;
    if (index == 6) return air.center6;
    return air.center7;
}

vec4 waveAt(int index) {
    if (index == 0) return air.wave0;
    if (index == 1) return air.wave1;
    if (index == 2) return air.wave2;
    if (index == 3) return air.wave3;
    if (index == 4) return air.wave4;
    if (index == 5) return air.wave5;
    if (index == 6) return air.wave6;
    return air.wave7;
}

vec4 coreAt(int index) {
    if (index == 0) return air.core0;
    if (index == 1) return air.core1;
    if (index == 2) return air.core2;
    if (index == 3) return air.core3;
    if (index == 4) return air.core4;
    if (index == 5) return air.core5;
    if (index == 6) return air.core6;
    return air.core7;
}

bool reconstruct(vec2 uv, float depth, out vec3 point) {
    // Canonical RHI clip Y and texture UV both increase downward; depth is [0,1].
    vec4 position = air.clipToRelative * vec4(uv * 2.0 - 1.0, depth, 1.0);
    if (abs(position.w) < 0.0000001) return false;
    point = position.xyz / position.w;
    return !any(isnan(point)) && !any(isinf(point));
}

bool project(vec3 point, out vec2 uv) {
    vec4 clip = air.relativeToClip * vec4(point, 1.0);
    if (clip.w <= 0.000001) return false;
    uv = clip.xy / clip.w * 0.5 + 0.5;
    return !any(isnan(uv)) && !any(isinf(uv));
}

bool sceneDistance(vec2 uv, vec3 rayOrigin, vec3 rayDirection, out float distance) {
    float depth = texture(worldDepth, uv).r;
    float clearDepth = air.viewport.w > 0.0 ? 1.0 : 0.0;
    if (abs(depth - clearDepth) < 0.0000001) {
        distance = 1.0e20;
        return true;
    }
    vec3 point;
    if (!reconstruct(uv, depth, point)) return false;
    distance = dot(point - rayOrigin, rayDirection);
    return distance >= 0.0;
}

void main() {
    vec4 original = texture(background, coordinates);
    result = original;
    vec3 nearPoint;
    vec3 middlePoint;
    float nearDepth = air.viewport.w > 0.0 ? 0.0 : 1.0;
    if (!reconstruct(coordinates, nearDepth, nearPoint) || !reconstruct(coordinates, 0.5, middlePoint)) return;
    vec3 rayVector = middlePoint - nearPoint;
    if (dot(rayVector, rayVector) < 0.0000001) return;
    vec3 ray = normalize(rayVector);
    float backgroundDistance;
    if (!sceneDistance(coordinates, nearPoint, ray, backgroundDistance)) return;
    vec2 displacement = vec2(0.0);
    float nearestWave = 1.0e20;
    for (int index = 0; index < 8; ++index) {
        if (float(index) >= air.viewport.z) break;
        float amplitude = waveAt(index).x;
        vec4 core = coreAt(index);
        if (amplitude <= 0.0 && core.y <= 0.0) continue;
        vec3 center = centerAt(index).xyz;
        float expansionRadius = centerAt(index).w;
        float radius = max(amplitude > 0.0 ? expansionRadius : 0.0, core.y > 0.0 ? core.x : 0.0);
        vec3 originOffset = nearPoint - center;
        float along = dot(originOffset, ray);
        float distanceSquared = dot(originOffset, originOffset) - along * along;
        float discriminant = radius * radius - distanceSquared;
        if (discriminant <= 0.0) continue;
        float root = sqrt(discriminant);
        float hitDistance = -along - root;
        if (hitDistance < 0.0) hitDistance = -along + root;
        // Conservatively lock receivers in front of the emitter's depth plane, even as the shell grows.
        float receiverMinimumDistance = max(hitDistance, -along);
        if (hitDistance <= 0.0 || backgroundDistance <= receiverMinimumDistance + 0.02) continue;
        vec3 hit = nearPoint + ray * hitDistance;
        vec3 normal = (hit - center) / radius;
        vec2 hitUv;
        vec2 normalUv;
        if (!project(hit, hitUv) || !project(hit + normal * max(radius * 0.02, 0.01), normalUv)) continue;
        vec2 screenNormal = (normalUv - hitUv) * air.viewport.xy;
        float normalLength = length(screenNormal);
        if (normalLength < 0.0001) continue;
        float impactRadius = sqrt(max(distanceSquared, 0.0));
        float shellDisplacement = 0.0;
        if (amplitude > 0.0) {
            float shellRadius = impactRadius / expansionRadius;
            float profile = exp(-pow((shellRadius - 0.88) / waveAt(index).y, 2.0));
            profile *= 1.0 - smoothstep(0.97, 1.0, shellRadius);
            shellDisplacement = amplitude * profile;
        }
        float coreDisplacement = 0.0;
        if (core.y > 0.0 && impactRadius < core.x) {
            float coreRadius = impactRadius / core.x;
            // The axial taper removes the direction singularity without excluding the central region.
            float envelope = smoothstep(0.0, 0.15, coreRadius) * (1.0 - smoothstep(0.55, 1.0, coreRadius));
            coreDisplacement = core.y * envelope * sin(coreRadius * 6.28318530718 * core.w - core.z);
        }
        displacement -= screenNormal / normalLength * (shellDisplacement + coreDisplacement);
        nearestWave = min(nearestWave, receiverMinimumDistance);
    }
    float pixels = length(displacement);
    if (pixels < 0.0001) return;
    displacement *= min(1.0, air.limits.x / pixels);
    vec2 sourceUv = coordinates + displacement / air.viewport.xy;
    vec2 halfTexel = 0.5 / air.viewport.xy;
    if (any(lessThan(sourceUv, halfTexel)) || any(greaterThan(sourceUv, vec2(1.0) - halfTexel))) return;
    float shiftedDistance;
    if (!sceneDistance(sourceUv, nearPoint, ray, shiftedDistance)) return;
    float allowedDifference = max(air.limits.y, min(backgroundDistance, shiftedDistance) * air.limits.z);
    if (abs(shiftedDistance - backgroundDistance) > allowedDifference || shiftedDistance <= nearestWave + 0.02) return;
    result = vec4(texture(background, sourceUv).rgb, original.a);
}
