#version 450
layout(set = 0, binding = 0) uniform sampler2D background;
layout(push_constant) uniform Metering { vec4 controls; } metering;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;
void main() {
    ivec2 extent=textureSize(background,0);
    ivec2 tiles=(extent+63)/64;
    ivec2 pixel=ivec2(coordinates*vec2(tiles.x*32,tiles.y));
    int slice=pixel.x/tiles.x;
    ivec2 tile=ivec2(pixel.x%tiles.x,pixel.y);
    uint packed[64];
    for (int thread=0;thread<64;++thread) packed[thread]=0u;
    for (int y=0;y<64;++y) for (int x=0;x<64;++x) {
        ivec2 source=tile*64+ivec2(x,y);
        if (any(greaterThanEqual(source,extent-extent%2))) continue;
        float value=texture(background,(vec2(source)+0.5)/vec2(extent)).r;
        float normalized=(value-metering.controls.x)/(metering.controls.y-metering.controls.x);
        float position=clamp(normalized,0.0,1.0)*31.0;
        float weight=max(0.0,1.0-abs(position-float(slice)));
        uint quantizedWeight=uint(weight*512.0)&65535u;
        uint quantizedLuminance=uint(normalized*weight*512.0)&65535u;
        int thread=(x%16)/2+8*((y%16)/2);
        packed[thread]+=(quantizedWeight<<16u)|quantizedLuminance;
    }
    vec2 sum=vec2(0.0);
    for (int thread=0;thread<64;++thread) {
        float weight=float(packed[thread]>>16u)/512.0;
        float normalizedSum=float(packed[thread]&65535u)/512.0;
        float logSum=normalizedSum*(metering.controls.y-metering.controls.x)+weight*metering.controls.x;
        sum+=vec2(logSum,weight);
    }
    result=vec4(sum,0.0,0.0);
}
