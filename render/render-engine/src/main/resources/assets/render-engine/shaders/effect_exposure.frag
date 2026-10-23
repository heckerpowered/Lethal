#version 450
layout(set=0,binding=0) uniform sampler2D content;
layout(set=0,binding=1) uniform sampler2D adaptedExposure;
layout(set=0,binding=2) uniform sampler2D background;
layout(set=0,binding=3) uniform sampler2D bilateral;
layout(set=0,binding=4) uniform sampler2D blurred;
layout(push_constant) uniform Settings { vec4 contrast; vec4 controls; vec4 histogramRange; vec4 thresholds; vec4 strengths; } settings;
layout(location=0) in vec2 coordinates;
layout(location=0) out vec4 result;
vec2 gridSlice(int slice,vec2 tile) {
    ivec2 extent=textureSize(bilateral,0);
    int columns=extent.x/32;
    ivec2 lower=ivec2(floor(tile));
    vec2 fraction=fract(tile);
    vec2 a=texture(bilateral,(vec2(ivec2(slice*columns+clamp(lower.x,0,columns-1),clamp(lower.y,0,extent.y-1)))+0.5)/vec2(extent)).rg;
    vec2 b=texture(bilateral,(vec2(ivec2(slice*columns+clamp(lower.x+1,0,columns-1),clamp(lower.y,0,extent.y-1)))+0.5)/vec2(extent)).rg;
    vec2 c=texture(bilateral,(vec2(ivec2(slice*columns+clamp(lower.x,0,columns-1),clamp(lower.y+1,0,extent.y-1)))+0.5)/vec2(extent)).rg;
    vec2 d=texture(bilateral,(vec2(ivec2(slice*columns+clamp(lower.x+1,0,columns-1),clamp(lower.y+1,0,extent.y-1)))+0.5)/vec2(extent)).rg;
    return mix(mix(a,b,fraction.x),mix(c,d,fraction.x),fraction.y);
}
void main() {
    vec4 source=texture(content,coordinates);
    float exposure=texture(adaptedExposure,vec2(0.5)).r;
    float gain=1.0;
    if (settings.controls.x>0.5) {
        vec3 radiance=source.rgb;
        // The background is an LDR proxy; effect radiance is retained for the range query/detail.
        float proxy=exp2(texture(background,coordinates).r);
        float logLum=log2(max(proxy+dot(radiance,vec3(1.0/3.0)),exp2(settings.histogramRange.x)));
        float slice=clamp((logLum-settings.histogramRange.x)/(settings.histogramRange.y-settings.histogramRange.x),0.0,1.0)*31.0;
        vec2 tile=coordinates*vec2(textureSize(background,0))/64.0-0.5;
        vec2 field=mix(gridSlice(int(floor(slice)),tile),gridSlice(min(int(floor(slice))+1,31),tile),fract(slice));
        float blur=texture(blurred,coordinates).r;
        float base=field.y>=0.001 ? field.x/field.y : blur;
        base=mix(base,blur,settings.contrast.w)+log2(exposure);
        float exposedLog=logLum+log2(exposure);
        // The automatic-exposure middle-grey reference includes exposure compensation.
        float middle=log2(0.18)+settings.controls.y+settings.controls.w;
        float center=base-middle;
        bool highlight=center>0.0;
        float threshold=highlight ? settings.thresholds.x : settings.thresholds.y;
        float strength=highlight ? settings.strengths.x : settings.strengths.y;
        float inner=highlight ? max(0.0,center-threshold) : min(0.0,center+threshold);
        float t=clamp((abs(center)-threshold)*(1.0-strength),0.0,1.0);
        float offset=(center-inner)*(1.0-t*t*(3.0-2.0*t));
        float contrast=highlight ? settings.contrast.x : settings.contrast.y;
        gain=exp2((contrast-1.0)*(center-offset)+(settings.contrast.z-1.0)*(exposedLog-base));
    }
    float global=settings.histogramRange.z>0.5 ? exposure : 1.0;
    result=vec4(source.rgb*global*gain,source.a);
}
