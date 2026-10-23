#version 450
layout(set=0,binding=0) uniform sampler2D response;
layout(set=0,binding=1) uniform sampler2D history;
layout(push_constant) uniform Settings{vec4 range;vec4 timing;} settings;
layout(location=0) out vec4 result;
void main(){
    float best=1e30;int selected=0;int minimumSafe=63;
    for(int i=0;i<64;++i){vec4 data=texture(response,vec2((float(i)+.5)/64.0,.5));if(data.a>.5)minimumSafe=min(minimumSafe,i);if(data.r<best){best=data.r;selected=i;}}
    float target=mix(settings.range.x,settings.range.y,float(max(selected,minimumSafe))/63.0),current=target;
    if(settings.timing.y>.5){
        float old=log2(max(texture(history,vec2(.5)).r,.000001));
        float desired=mix(old,target,1.0-exp(-settings.timing.x/settings.range.w));
        current=old+clamp(desired-old,-settings.range.z*settings.timing.x,settings.range.z*settings.timing.x);
    }
    current=max(current,min(target,mix(settings.range.x,settings.range.y,float(minimumSafe)/63.0)));
    current=clamp(current,settings.range.x,settings.range.y);
    float span=settings.range.y-settings.range.x;
    int index=span>0.000001 ? int(clamp(round((current-settings.range.x)/span*63.0),0.0,63.0)) : 0;
    vec4 prediction=texture(response,vec2((float(index)+.5)/64.0,.5));
    result=vec4(exp2(current),exp2(target),prediction.g,prediction.b);
}
