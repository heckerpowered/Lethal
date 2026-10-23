#version 450
layout(set=0,binding=0) uniform sampler2D tiles;
layout(location=0) in vec2 coordinates;
layout(location=0) out vec4 result;
void main(){
    ivec2 extent=textureSize(tiles,0),tileCount=extent/ivec2(48,18);
    int group=int(floor(coordinates.y*36.0))/9;
    ivec2 cell=ivec2(floor(coordinates.x*16.0),mod(floor(coordinates.y*36.0),9.0));
    vec4 energy=vec4(0.0);
    vec3 background=vec3(0.0);
    float alpha=0.0,weightSum=0.0;
    for(int y=0;y<tileCount.y;++y)for(int x=0;x<tileCount.x;++x){
        ivec2 pixel=ivec2((cell.x*tileCount.x+x)*3,(group/2*9+cell.y)*tileCount.y+y);
        vec2 uv=(vec2(pixel)+.5)/vec2(extent);
        vec4 localBackground=texture(tiles,uv+vec2(1.0/float(extent.x),0.0));
        weightSum+=texture(tiles,uv+vec2(2.0/float(extent.x),0.0)).r;
        if(group%2==0){energy+=texture(tiles,uv);alpha+=localBackground.a;}
        else background+=localBackground.rgb;
    }
    if(weightSum==0.0){result=vec4(0.0);return;}
    if(group%2==0){
        vec3 representative=energy.rgb/weightSum*exp2(energy.a/weightSum);
        result=vec4(representative,alpha/weightSum);
    }else result=vec4(pow(background/weightSum,vec3(2.0)),weightSum);
}
