#version 450

layout(set = 0, binding = 0) uniform sampler2D background;
layout(set = 0, binding = 1) uniform sampler2D worldDepth;
layout(std140, set = 1, binding = 0) uniform AirShockwave {
    mat4 relativeToClip;
    mat4 clipToRelative;
    vec4 viewport; // width, height, count, conventional/reversed depth direction
    vec4 limits; // total pixel limit, absolute depth edge tolerance, relative depth edge tolerance
    // Camera-relative center and expanding shell radius.
    vec4 center0;
    vec4 center1;
    vec4 center2;
    vec4 center3;
    vec4 center4;
    vec4 center5;
    vec4 center6;
    vec4 center7;
    // Shell pixel amplitude and band width fraction; z/w are unused.
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

struct Shockwave {
    vec3 center;
    float expansionRadius;
    float shellAmplitudePixels;
    float shellWidthFraction;
    float rippleRadius;
    float rippleAmplitudePixels;
    float ripplePhase;
    float rippleSpatialCycles;
};

Shockwave unpackShockwave(vec4 centerAndRadius, vec4 shell, vec4 ripple) {
    return Shockwave(centerAndRadius.xyz, centerAndRadius.w, shell.x, shell.y,
    ripple.x, ripple.y, ripple.z, ripple.w);
}

// OpenGL lowering and generated parameter writers require flat, non-array block members.
// Keep that storage layout here; the refraction calculation uses one source with named fields.
Shockwave readShockwave(int index) {
    if (index == 0) return unpackShockwave(air.center0, air.wave0, air.core0);
    if (index == 1) return unpackShockwave(air.center1, air.wave1, air.core1);
    if (index == 2) return unpackShockwave(air.center2, air.wave2, air.core2);
    if (index == 3) return unpackShockwave(air.center3, air.wave3, air.core3);
    if (index == 4) return unpackShockwave(air.center4, air.wave4, air.core4);
    if (index == 5) return unpackShockwave(air.center5, air.wave5, air.core5);
    if (index == 6) return unpackShockwave(air.center6, air.wave6, air.core6);
    return unpackShockwave(air.center7, air.wave7, air.core7);
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

float shellDisplacementPixels(Shockwave source, float impactRadius) {
    if (source.shellAmplitudePixels <= 0.0) return 0.0;
    float radiusFraction = impactRadius / source.expansionRadius;
    float profile = exp(-pow((radiusFraction - 0.88) / source.shellWidthFraction, 2.0));
    profile *= 1.0 - smoothstep(0.97, 1.0, radiusFraction);
    return source.shellAmplitudePixels * profile;
}

float rippleDisplacementPixels(Shockwave source, float impactRadius) {
    if (source.rippleAmplitudePixels <= 0.0 || impactRadius >= source.rippleRadius) return 0.0;
    float radiusFraction = impactRadius / source.rippleRadius;
    // The axial taper removes the direction singularity without excluding the central region.
    float envelope = smoothstep(0.0, 0.15, radiusFraction) * (1.0 - smoothstep(0.55, 1.0, radiusFraction));
    return source.rippleAmplitudePixels * envelope * sin(radiusFraction * 6.28318530718 * source.rippleSpatialCycles - source.ripplePhase);
}

bool shockwaveDisplacement(Shockwave source, vec3 rayOrigin, vec3 rayDirection, float backgroundDistance, out vec2 displacementPixels, out float receiverMinimumDistance) {
    if (source.shellAmplitudePixels <= 0.0 && source.rippleAmplitudePixels <= 0.0) return false;

    float radius = max(source.shellAmplitudePixels > 0.0 ? source.expansionRadius : 0.0,
                       source.rippleAmplitudePixels > 0.0 ? source.rippleRadius : 0.0);
    vec3 originOffset = rayOrigin - source.center;
    float centerProjection = dot(originOffset, rayDirection);
    float distanceSquared = dot(originOffset, originOffset) - centerProjection * centerProjection;
    float discriminant = radius * radius - distanceSquared;
    if (discriminant <= 0.0) return false;

    float root = sqrt(discriminant);
    float hitDistance = -centerProjection - root;
    if (hitDistance < 0.0) hitDistance = -centerProjection + root;

    // Conservatively lock receivers in front of the emitter's depth plane, even as the shell grows.
    receiverMinimumDistance = max(hitDistance, -centerProjection);
    if (hitDistance <= 0.0 || backgroundDistance <= receiverMinimumDistance + 0.02) return false;

    vec3 hit = rayOrigin + rayDirection * hitDistance;
    vec3 normal = (hit - source.center) / radius;
    vec2 hitUv;
    vec2 normalUv;
    if (!project(hit, hitUv) || !project(hit + normal * max(radius * 0.02, 0.01), normalUv)) return false;

    vec2 screenNormal = (normalUv - hitUv) * air.viewport.xy;
    float normalLength = length(screenNormal);
    if (normalLength < 0.0001) return false;

    float impactRadius = sqrt(max(distanceSquared, 0.0));
    float shell = shellDisplacementPixels(source, impactRadius);
    float ripple = rippleDisplacementPixels(source, impactRadius);
    displacementPixels = screenNormal / normalLength * (shell + ripple);
    return true;
}

void main() {
    vec4 original = texture(background, coordinates);
    result = original;

    vec3 rayOrigin;
    vec3 middlePoint;
    float nearDepth = air.viewport.w > 0.0 ? 0.0 : 1.0;
    if (!reconstruct(coordinates, nearDepth, rayOrigin) || !reconstruct(coordinates, 0.5, middlePoint)) return;

    vec3 rayVector = middlePoint - rayOrigin;
    if (dot(rayVector, rayVector) < 0.0000001) return;

    vec3 rayDirection = normalize(rayVector);
    float backgroundDistance;
    if (!sceneDistance(coordinates, rayOrigin, rayDirection, backgroundDistance)) return;

    vec2 displacementPixels = vec2(0.0);
    float nearestReceiverDistance = 1.0e20;
    for (int index = 0; index < 8; ++index) {
        if (float(index) >= air.viewport.z) break;
        vec2 sourceDisplacementPixels;
        float receiverMinimumDistance;
        if (!shockwaveDisplacement(readShockwave(index), rayOrigin, rayDirection, backgroundDistance, sourceDisplacementPixels, receiverMinimumDistance)) continue;
        displacementPixels -= sourceDisplacementPixels;
        nearestReceiverDistance = min(nearestReceiverDistance, receiverMinimumDistance);
    }

    float displacementLength = length(displacementPixels);
    if (displacementLength < 0.0001) return;
    displacementPixels *= min(1.0, air.limits.x / displacementLength);

    vec2 sourceUv = coordinates + displacementPixels / air.viewport.xy;
    vec2 halfTexel = 0.5 / air.viewport.xy;
    if (any(lessThan(sourceUv, halfTexel)) || any(greaterThan(sourceUv, vec2(1.0) - halfTexel))) return;

    float shiftedDistance;
    if (!sceneDistance(sourceUv, rayOrigin, rayDirection, shiftedDistance)) return;

    float allowedDifference = max(air.limits.y, min(backgroundDistance, shiftedDistance) * air.limits.z);
    if (abs(shiftedDistance - backgroundDistance) > allowedDifference || shiftedDistance <= nearestReceiverDistance + 0.02) return;

    result = vec4(texture(background, sourceUv).rgb, original.a);
}
