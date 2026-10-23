#version 450
layout(push_constant) uniform Reference { vec4 viewport; vec4 emitter; } reference;
layout(location=0) in vec2 coordinates;
layout(location=0) out vec4 result;
void main() {
    vec2 position=coordinates*reference.viewport.xy;
    vec2 center=vec2(reference.viewport.x-reference.viewport.z,reference.viewport.y-reference.viewport.w);
    float coverage=1.0-smoothstep(reference.emitter.y-0.5,reference.emitter.y+0.5,length(position-center));
    result=vec4(vec3(reference.emitter.x)*coverage,coverage);
}
