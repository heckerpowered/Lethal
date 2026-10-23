#version 450
layout(set=0,binding=0) uniform sampler2D source;
layout(set=0,binding=1) uniform sampler2D content;
layout(set=0,binding=2) uniform sampler2D glow;
layout(set=0,binding=3) uniform sampler2D background;
layout(location=0) in vec2 coordinates;
layout(location=0) out vec4 result;
const vec3 luminanceWeights=vec3(.2126,.7152,.0722);
void main(){
    ivec2 extent=textureSize(source,0);
    ivec2 maximumSpan=extent/ivec2(16,9);
    if(extent.x%16!=0)maximumSpan.x+=2;
    if(extent.y%9!=0)maximumSpan.y+=2;
    ivec2 tileCount=(maximumSpan+15)/16;
    ivec2 pixel=ivec2(coordinates*vec2(tileCount.x*48,tileCount.y*18));
    int plane=pixel.x%3,group=pixel.y/(tileCount.y*9);
    ivec2 cell=ivec2(pixel.x/(tileCount.x*3),(pixel.y/tileCount.y)%9);
    ivec2 tile=ivec2((pixel.x/3)%tileCount.x,pixel.y%tileCount.y);
    ivec2 lower=ivec2(floor(vec2(cell)*vec2(extent)/vec2(16.0,9.0)));
    ivec2 upper=ivec2(ceil(vec2(cell+1)*vec2(extent)/vec2(16.0,9.0)));
    vec3 hue=vec3(0.0),bg=vec3(0.0);
    float logEnergy=0.0,alpha=0.0,weightSum=0.0;
    for(int y=0;y<16;++y)for(int x=0;x<16;++x){
        ivec2 samplePixel=lower+tile*16+ivec2(x,y);
        if(any(greaterThanEqual(samplePixel,upper)))continue;
        vec2 uv=(vec2(samplePixel)+.5)/vec2(extent);
        vec3 raw=texture(source,uv).rgb,halo=texture(glow,uv).rgb;
        float rawEnergy=max(dot(max(raw,vec3(0.0)),luminanceWeights),0.0);
        float haloEnergy=max(dot(max(halo,vec3(0.0)),luminanceWeights),0.0);
        float weight=group==0 ? smoothstep(.001,.02,rawEnergy) : (1.0-smoothstep(.001,.02,rawEnergy))*smoothstep(.002,.02,haloEnergy);
        if(plane==2){weightSum+=weight;continue;}
        vec4 surface=texture(content,uv);
        if(plane==1){
            bg+=sqrt(clamp(texture(background,uv).rgb,vec3(0.0),vec3(1.0)))*weight;
            alpha+=clamp(surface.a,0.0,1.0)*weight;
        }else{
            vec3 total=max(surface.rgb+halo,vec3(0.0));
            float energy=max(dot(total,luminanceWeights),.000001);
            hue+=total/energy*weight;logEnergy+=log2(energy)*weight;
        }
    }
    result=plane==0 ? vec4(hue,logEnergy) : plane==1 ? vec4(bg,alpha) : vec4(weightSum,0.0,0.0,0.0);
}
