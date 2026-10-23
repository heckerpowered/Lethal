#version 450
layout(location = 0) in vec2 surfacePoint;
layout(location = 1) in vec4 sparkState;
layout(location = 0) out vec4 result;
layout(push_constant) uniform Element {
    mat4 clipFromLocal;
    vec4 timing;
    vec4 appearance;
    vec4 cameraLocal;
} element;

void main() {
    if (sparkState.w < .5 || element.timing.x < 0.0 || element.timing.x >= min(element.timing.y, .72)) discard;
    float along = surfacePoint.x;
    float across = abs(surfacePoint.y);
    float edge = 1.0 - smoothstep(.18, 1.0, across);
    float tail = smoothstep(0.0, .10, along) * (.10 + .90 * pow(along, .65));
    float hotTip = smoothstep(.79, .97, along) * (1.0 - smoothstep(.98, 1.0, along));
    float ignition = smoothstep(0.0, .010, sparkState.y);
    float decay = 1.0 - smoothstep(.40, 1.0, sparkState.x);
    float flicker = .88 + .12 * sin(element.timing.x * 97.0 + sparkState.z * 6.2831853);
    float coverage = edge * tail * ignition * decay * flicker * .52;
    if (coverage < .0001) discard;
    vec3 blue = vec3(.20, .45, 1.0);
    float luminance = dot(blue, vec3(.2126, .7152, .0722));
    vec3 color = mix(blue, vec3(luminance), .72 * hotTip * pow(max(1.0 - across, 0.0), 3.0));
    float radiance = 2.4 + 2.6 * hotTip;
    result = vec4(color * radiance * coverage * element.appearance.x, coverage);
}
