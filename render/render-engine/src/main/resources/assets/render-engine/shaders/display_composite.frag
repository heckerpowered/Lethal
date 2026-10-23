#version 450
layout(set = 0, binding = 0) uniform sampler2D background;
layout(set = 0, binding = 1) uniform sampler2D content;
layout(set = 0, binding = 2) uniform sampler2D glow;
layout(push_constant) uniform Composition { vec4 controls; } composition;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;
vec3 decodeSrgb(vec3 color) {
    return mix(pow((color + 0.055) / 1.055, vec3(2.4)), color / 12.92, lessThanEqual(color, vec3(0.04045)));
}
vec3 encodeSrgb(vec3 color) {
    color = max(color, vec3(0.0));
    return mix(1.055 * pow(color, vec3(1.0 / 2.4)) - 0.055, 12.92 * color, lessThanEqual(color, vec3(0.0031308)));
}
void main() {
    vec4 destination = texture(background, coordinates);
    if (composition.controls.y > 3.5) {
        result = vec4(composition.controls.x > 0.5 ? decodeSrgb(destination.rgb) : destination.rgb, destination.a);
        return;
    }
    vec4 surface = composition.controls.z > 0.5 ? texture(content, coordinates) : vec4(0.0);
    vec4 halo = composition.controls.w > 0.5 ? texture(glow, coordinates) : vec4(0.0);
    if (all(equal(surface, vec4(0.0))) && all(equal(halo, vec4(0.0)))) {
        result = destination;
        return;
    }
    vec3 destinationLinear = composition.controls.x > 0.5 ? decodeSrgb(destination.rgb) : destination.rgb;
    // Match numeric source-alpha clipping when the destination is normalized storage.
    float sourceAlpha = composition.controls.y > 1.5 && composition.controls.y < 2.5 ? clamp(surface.a, 0.0, 1.0) : surface.a;
    float transmission = composition.controls.y > 0.5 ? 1.0 - sourceAlpha : 1.0;
    vec3 combined = destinationLinear * transmission + surface.rgb + halo.rgb;
    float alpha = composition.controls.y > 1.5 ? sourceAlpha + destination.a * transmission + halo.a : destination.a;
    result = vec4(composition.controls.x > 0.5 ? encodeSrgb(combined) : combined, alpha);
}
