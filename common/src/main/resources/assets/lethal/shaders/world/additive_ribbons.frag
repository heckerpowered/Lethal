#version 450
layout(location=0) in vec3 surfacePoint;
layout(location=1) in vec3 localPosition;
layout(location=2) in vec4 lifeState;
layout(location=0) out vec4 result;
layout(push_constant) uniform Explosion {
    mat4 clipFromLocal; vec4 timing; vec4 appearance; vec4 cameraLocal;
} explosion;
const float overallBrightness=.096;
float hash(float x) { return fract(sin(x*17.13+explosion.timing.z*3.71)*43758.5453); }
vec3 bodyRadiance() {
    vec3 color=vec3(.2,.45,1.0);
    return color*(dot(vec3(0.0,.1,1000.0),vec3(.2126,.7152,.0722))/dot(color,vec3(.2126,.7152,.0722)));
}
void main() {
    float layer=explosion.timing.w;
    float envelope=explosion.appearance.y;
    float coverage;
    vec3 radiance;
    if(layer<.5) {
        vec3 sight=normalize(explosion.cameraLocal.xyz-localPosition);
        float facing=dot(normalize(surfacePoint),sight)*explosion.cameraLocal.w;
        if(facing<=0.0)discard;
        coverage=smoothstep(.25,.7,facing)*envelope;
        float white=pow(max(facing,0.0),24.0);
        float luminance=dot(bodyRadiance(),vec3(.2126,.7152,.0722));
        radiance=mix(bodyRadiance()*.8,vec3(luminance),white)*coverage;
    } else if(layer<1.5) {
        float side=abs(surfacePoint.y);
        float line=1.0-smoothstep(.60,1.0,side);
        float ends=smoothstep(0.0,.05,surfacePoint.x)*(1.0-smoothstep(.94,1.0,surfacePoint.x));
        coverage=line*ends*lifeState.z*envelope*mix(.46,.29,lifeState.y);
        float white=pow(max(1.0-side,0.0),6.0)*.45;
        float luminance=dot(bodyRadiance(),vec3(.2126,.7152,.0722));
        radiance=mix(bodyRadiance(),vec3(luminance),white)*coverage;
    } else {
        float u=surfacePoint.x;
        float side=1.0-smoothstep(.2,1.0,abs(surfacePoint.y));
        float segment=floor(u*7.0);
        float broken=step(.32,hash(segment+lifeState.x*11.0));
        float ends=smoothstep(0.0,.08,u)*(1.0-smoothstep(.82,1.0,u));
        coverage=side*broken*ends*envelope*.09;
        radiance=bodyRadiance()*.18*coverage;
    }
    if(coverage<.0001)discard;
    result=vec4(radiance*explosion.appearance.w*overallBrightness,clamp(coverage*.04,0.0,1.0));
}
