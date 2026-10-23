#version 450
layout(set=0,binding=0) uniform sampler2D adaptedExposure;
layout(push_constant) uniform Strength { vec4 controls; } settings;
layout(location=0) out vec4 result;
void main() {
    vec4 adapted=texture(adaptedExposure,vec2(0.5));
    vec2 gain=settings.controls.x==0.0 ? vec2(1.0)
        : exp2(log2(max(adapted.rg,vec2(1e-30)))*settings.controls.x);
    result=vec4(gain,adapted.b,exp2(settings.controls.y));
}
