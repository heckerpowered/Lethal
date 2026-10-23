#version 450
layout(set = 0, binding = 0) uniform sampler2D background;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;
void main() {
    int bucket = int(floor(coordinates.x*64.0));
    uint lowWord=0u, highWord=0u;
    ivec2 extent=textureSize(background,0);
    for (int y=0;y<extent.y;++y) for (int x=0;x<extent.x/64;++x) {
        vec2 words=texture(background,(vec2(x*64+bucket,y)+0.5)/vec2(extent)).rg;
        uint packed=uint(words.x)|(uint(words.y)<<16u);
        uint previous=lowWord;
        lowWord+=packed;
        if (lowWord<previous) highWord+=1u;
    }
    float count=float(highWord)*8192.0+float(lowWord)/524288.0;
    result = vec4(count,0.0,0.0,0.0);
}
