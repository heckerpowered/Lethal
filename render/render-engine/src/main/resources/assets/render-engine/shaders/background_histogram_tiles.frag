#version 450
layout(set = 0, binding = 0) uniform sampler2D background;
layout(push_constant) uniform Metering { vec4 controls; } metering;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;
void main() {
    ivec2 extent=textureSize(background,0);
    ivec2 tiles=(extent+15)/16;
    ivec2 pixel=ivec2(coordinates*vec2(tiles.x*64,tiles.y));
    float bucket=float(pixel.x%64);
    ivec2 tile=ivec2(pixel.x/64,pixel.y);
    uint packed=0u;
    for (int y=0;y<16;++y) for (int x=0;x<16;++x) {
        ivec2 source=tile*16+ivec2(x,y);
        if (any(greaterThanEqual(source,extent))) continue;
        float value=texture(background,(vec2(source)+0.5)/vec2(extent)).r;
        float position=clamp((value-metering.controls.x)/(metering.controls.y-metering.controls.x),0.0,1.0)*63.0;
        float weight=max(0.0,1.0-abs(position-bucket));
        if (bucket<0.5) weight*=metering.controls.z;
        packed+=uint(weight*524288.0);
    }
    // A tile sums at most 2^27; two exact FP32 halfwords preserve every quantized weight for reduction.
    result=vec4(float(packed&65535u),float(packed>>16u),0.0,0.0);
}
