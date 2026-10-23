#version 450
layout(location=0) in vec3 coordinates;
layout(location=0) out vec3 surfacePoint;
layout(location=1) out vec3 localPosition;
layout(location=2) out vec4 lifeState;
layout(push_constant) uniform Explosion {
    mat4 clipFromLocal; vec4 timing; vec4 appearance; vec4 cameraLocal;
} explosion;
const float PI=3.141592653589793;
float random(float id) { return fract(sin(id*17.13+explosion.timing.z*3.71)*43758.5453); }
float triangle(float x) { return abs(fract(x)-0.5)*4.0-1.0; }
vec3 circlePoint(float id,float angle) {
    float yaw=id*1.71+explosion.timing.z*.37;
    vec3 right=vec3(cos(yaw),0.0,sin(yaw));
    vec3 up=normalize(vec3(-sin(yaw)*.3,1.0,cos(yaw)*.3));
    return right*cos(angle)+up*sin(angle);
}
vec3 mainArc(float id,float u) {
    float angle=(random(id)*2.0-1.0)*PI+(u-.5)*1.12;
    vec3 normal=circlePoint(id,angle);
    vec3 sideways=circlePoint(id,angle+PI*.5);
    float jagged=(triangle(u*7.0+id)*.14+triangle(u*13.0-id)*.055)*sin(PI*u);
    return normal*explosion.appearance.z+sideways*jagged;
}
vec3 arcPoint(float id,float u) {
    if(id<4.5)return mainArc(id,u);
    float branch=id-5.0;
    float parent=floor(branch*.5);
    float second=mod(branch,2.0);
    float attach=mix(.31,.66,second);
    vec3 root=mainArc(parent,attach);
    vec3 tangent=normalize(mainArc(parent,attach+.01)-mainArc(parent,attach-.01));
    vec3 radial=normalize(root);
    vec3 direction=normalize(radial*.65+tangent*mix(-.7,.7,second));
    vec3 normal=normalize(cross(radial,tangent));
    float jagged=triangle(u*5.0+id)*.10*sin(PI*u);
    return root+direction*explosion.appearance.z*.31*u+normal*jagged;
}
float flash(float age,float start,float duration) {
    float u=(age-start)/duration;
    return smoothstep(0.0,.12,u)*(1.0-smoothstep(.48,1.0,u));
}
void main() {
    float age=explosion.timing.x;
    float layer=explosion.timing.w;
    float id=coordinates.x;
    float u=coordinates.y;
    if(layer<.5) {
        surfacePoint=coordinates;
        localPosition=coordinates*explosion.appearance.z;
        lifeState=vec4(0.0,0.0,1.0,1.0);
    } else {
        vec3 position;
        vec3 tangent;
        float width;
        float gate=1.0;
        if(layer<1.5) {
            position=arcPoint(id,u);
            tangent=arcPoint(id,min(u+.01,1.0))-arcPoint(id,max(u-.01,0.0));
            float parent=id<4.5?id:floor((id-5.0)*.5);
            float offset=random(parent+8.0)*.013;
            gate=flash(age,.17+offset,.065)+.65*flash(age,.29+offset,.045);
            width=id<4.5?.045:.018;
        } else {
            float angle=random(id+15.0)*2.0*PI+(u-.5)*(.50+random(id+2.0)*.30)+age*.65;
            vec3 direction=circlePoint(id+.4,angle);
            float ripple=sin(u*14.0+id*3.0-age*22.0)*.045;
            position=direction*(explosion.appearance.z+ripple);
            tangent=circlePoint(id+.4,angle+PI*.5);
            width=(.075+random(id)*.035)*sin(PI*u);
        }
        vec3 sight=explosion.cameraLocal.xyz-position;
        vec3 sideways=cross(tangent,sight);
        sideways/=max(length(sideways),.0001);
        localPosition=position+sideways*coordinates.z*width;
        surfacePoint=vec3(u,coordinates.z,1.0);
        lifeState=vec4(id,step(4.5,id),gate,width);
    }
    gl_Position=explosion.clipFromLocal*vec4(localPosition,1.0);
}
