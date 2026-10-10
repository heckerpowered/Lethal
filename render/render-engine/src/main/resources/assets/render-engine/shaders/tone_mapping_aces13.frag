#version 450

/* ACES 1.3 reference RRT + RGBmonitor_100nits_dim ODT adapted from Academy CTL.
 * Copyright 2015 Academy of Motion Picture Arts and Sciences (A.M.P.A.S.).
 * License conditions and warranty disclaimer are reproduced in the distributed
 * assets/render-engine/licenses/ACES-1.3.txt. No UE Filmic/LUT code is included.
 * Matrices use column vectors and Bradford D65 <-> D60 adaptation.
 */
/*
# License Terms for Academy Color Encoding System Components #

Academy Color Encoding System (ACES) software and tools are provided by the
Academy under the following terms and conditions: A worldwide, royalty-free,
non-exclusive right to copy, modify, create derivatives, and use, in source and
binary forms, is hereby granted, subject to acceptance of this license.

Copyright © 2015 Academy of Motion Picture Arts and Sciences (A.M.P.A.S.).
Portions contributed by others as indicated. All rights reserved.

Performance of any of the aforementioned acts indicates acceptance to be bound
by the following terms and conditions:

* Copies of source code, in whole or in part, must retain the above copyright
notice, this list of conditions and the Disclaimer of Warranty.

* Use in binary form must retain the above copyright notice, this list of
conditions and the Disclaimer of Warranty in the documentation and/or other
materials provided with the distribution.

* Nothing in this license shall be deemed to grant any rights to trademarks,
copyrights, patents, trade secrets or any other intellectual property of
A.M.P.A.S. or any contributors, except as expressly stated herein.

* Neither the name "A.M.P.A.S." nor the name of any other contributors to this
software may be used to endorse or promote products derivative of or based on
this software without express prior written permission of A.M.P.A.S. or the
contributors, as appropriate.

This license shall be construed pursuant to the laws of the State of
California, and any disputes related thereto shall be subject to the
jurisdiction of the courts therein.

Disclaimer of Warranty: THIS SOFTWARE IS PROVIDED BY A.M.P.A.S. AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND
NON-INFRINGEMENT ARE DISCLAIMED. IN NO EVENT SHALL A.M.P.A.S., OR ANY
CONTRIBUTORS OR DISTRIBUTORS, BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, RESITUTIONARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

WITHOUT LIMITING THE GENERALITY OF THE FOREGOING, THE ACADEMY SPECIFICALLY
DISCLAIMS ANY REPRESENTATIONS OR WARRANTIES WHATSOEVER RELATED TO PATENT OR
OTHER INTELLECTUAL PROPERTY RIGHTS IN THE ACADEMY COLOR ENCODING SYSTEM, OR
APPLICATIONS THEREOF, HELD BY PARTIES OTHER THAN A.M.P.A.S.,WHETHER DISCLOSED OR
UNDISCLOSED.
*/
layout(set = 0, binding = 0) uniform sampler2D content;
layout(set = 0, binding = 1) uniform sampler2D glow;
layout(push_constant) uniform ToneMapping { vec4 mapping; vec2 controls; } toneMapping;
layout(location = 0) in vec2 coordinates;
layout(location = 0) out vec4 result;
const mat3 SRGB_TO_AP0 = mat3(0.439632981919, 0.089776442959, 0.017541170383, 0.382988698152, 0.813439428749, 0.111546553302, 0.177378319929, 0.096784128292, 0.870912276314);
const mat3 AP0_TO_AP1 = mat3(1.451439316146, -0.076553773396, 0.008316148426, -0.236510746894, 1.176229699834, -0.006032449791, -0.214928569252, -0.099675926438, 0.997716301365);
const mat3 AP1_TO_XYZ = mat3(0.662454181109, 0.272228716781, -0.005574649490, 0.134004206456, 0.674081765811, 0.004060733529, 0.156187687005, 0.053689517408, 1.010339100313);
const mat3 XYZ_TO_AP1 = mat3(1.641023379694, -0.663662858723, 0.011721894328, -0.324803294185, 1.615331591657, -0.008284441996, -0.236424695238, 0.016756347686, 0.988394858539);
const mat3 AP1_TO_SRGB = mat3(1.705050992658, -0.130256417507, -0.024003356805, -0.621792120657, 1.140804736575, -0.128968976065, -0.083258872001, -0.010548319068, 1.152972332870);
const vec3 AP1_LUMA = vec3(0.2722287168, 0.6740817658, 0.0536895174);
float log10Value(float x) { return log2(x) / log2(10.0); }
float splineSegment(vec3 c, float t) {
    return dot(vec3(t*t, t, 1.0), vec3(0.5*c.x-c.y+0.5*c.z, -c.x+c.y, 0.5*c.x+0.5*c.y));
}
float rrtSpline(float x) {
    const float low[6] = float[6](-4.0,-4.0,-3.1573765773,-0.4852499958,1.8477324706,1.8477324706);
    const float high[6] = float[6](-0.7185482425,2.0810307172,3.6681241237,4.0,4.0,4.0);
    float v = log10Value(max(x, 0.00006103515625));
    float minimum = log10Value(0.18 * exp2(-15.0));
    float middle = log10Value(0.18);
    float maximum = log10Value(0.18 * exp2(18.0));
    if (v <= minimum) return 0.0001;
    if (v >= maximum) return 10000.0;
    bool lower = v < middle;
    float k = lower ? 3.0*(v-minimum)/(middle-minimum) : 3.0*(v-middle)/(maximum-middle);
    int j = min(int(k), 2);
    vec3 c = lower ? vec3(low[j],low[j+1],low[j+2]) : vec3(high[j],high[j+1],high[j+2]);
    return pow(10.0, splineSegment(c,k-float(j)));
}
float odtSpline(float x) {
    const float low[10] = float[10](-1.6989700043,-1.6989700043,-1.4779,-1.2291,-0.8648,-0.448,0.00518,0.4511080334,0.9113744414,0.9113744414);
    const float high[10] = float[10](0.5154386965,0.8470437783,1.1358,1.3802,1.5197,1.5985,1.6467,1.6746091357,1.6878733390,1.6878733390);
    float v = log10Value(max(x, 0.00006103515625));
    float minimum = log10Value(rrtSpline(0.18 * exp2(-6.5)));
    float middle = log10Value(rrtSpline(0.18));
    float maximum = log10Value(rrtSpline(0.18 * exp2(6.5)));
    if (v <= minimum) return pow(10.0,log10Value(0.02));
    if (v >= maximum) return pow(10.0,0.04*(v-maximum)+log10Value(48.0));
    bool lower = v < middle;
    float k = lower ? 7.0*(v-minimum)/(middle-minimum) : 7.0*(v-middle)/(maximum-middle);
    int j = min(int(k), 6);
    vec3 c = lower ? vec3(low[j],low[j+1],low[j+2]) : vec3(high[j],high[j+1],high[j+2]);
    return pow(10.0,splineSegment(c,k-float(j)));
}
float redHueWeight(float hue) {
    const float width = 135.0;
    if (abs(hue) >= width/2.0) return 0.0;
    float k = (hue+width/2.0)*4.0/width;
    int j = int(k);
    float t = k-float(j);
    vec4 powers = vec4(t*t*t,t*t,t,1.0);
    vec4 c;
    if (j == 0) c = vec4(1.0,0.0,0.0,0.0)/6.0;
    else if (j == 1) c = vec4(-3.0,3.0,3.0,1.0)/6.0;
    else if (j == 2) c = vec4(3.0,-6.0,0.0,4.0)/6.0;
    else c = vec4(-1.0,3.0,-3.0,1.0)/6.0;
    return 1.5*dot(powers,c);
}
vec3 acesDisplayLinear(vec3 linearSrgb) {
    vec3 aces = SRGB_TO_AP0 * linearSrgb;
    float maximum = max(max(aces.r,aces.g),aces.b);
    float minimum = min(min(aces.r,aces.g),aces.b);
    float saturation = (max(maximum,1e-10)-max(minimum,1e-10))/max(maximum,1e-2);
    float chroma = sqrt(max(aces.b*(aces.b-aces.g)+aces.g*(aces.g-aces.r)+aces.r*(aces.r-aces.b),0.0));
    float yc = (aces.r+aces.g+aces.b+1.75*chroma)/3.0;
    float shaperX = (saturation-0.4)/0.2;
    float t = max(1.0-abs(shaperX/2.0),0.0);
    float sigmoid = (1.0+sign(shaperX)*(1.0-t*t))/2.0;
    float gain = 0.05*sigmoid;
    float addedGlow = yc <= 2.0/3.0*0.08 ? gain : (yc >= 2.0*0.08 ? 0.0 : gain*(0.08/yc-0.5));
    aces *= 1.0+addedGlow;
    float hue = 0.0;
    if (aces.r != aces.g || aces.g != aces.b) hue = degrees(atan(sqrt(3.0)*(aces.g-aces.b),2.0*aces.r-aces.g-aces.b));
    aces.r += redHueWeight(hue)*saturation*(0.03-aces.r)*(1.0-0.82);
    vec3 rgb = clamp(AP0_TO_AP1 * max(aces,vec3(0.0)),vec3(0.0),vec3(65504.0));
    rgb = mix(vec3(dot(rgb,AP1_LUMA)),rgb,0.96);
    // RRT AP1 -> AP0 -> ODT AP1 cancel exactly; retain rendering-space RGB.
    rgb = vec3(rrtSpline(rgb.r),rrtSpline(rgb.g),rrtSpline(rgb.b));
    rgb = vec3(odtSpline(rgb.r),odtSpline(rgb.g),odtSpline(rgb.b));
    float black = pow(10.0,log10Value(0.02));
    rgb = (rgb-vec3(black))/(48.0-black);
    vec3 xyz = AP1_TO_XYZ * rgb;
    float total = xyz.x+xyz.y+xyz.z;
    if (total > 0.0 && xyz.y > 0.0) xyz *= pow(xyz.y,0.9811)/xyz.y;
    else xyz = vec3(0.0);
    rgb = XYZ_TO_AP1 * xyz;
    rgb = mix(vec3(dot(rgb,AP1_LUMA)),rgb,0.93);
    return clamp(AP1_TO_SRGB * rgb,vec3(0.0),vec3(1.0));
}
vec3 encodeSrgb(vec3 rgb) {
    return mix(1.055*pow(rgb,vec3(1.0/2.4))-0.055,12.92*rgb,lessThanEqual(rgb,vec3(0.0031308)));
}
void main() {
    vec4 surface = texture(content,coordinates);
    vec4 halo = texture(glow,coordinates)*toneMapping.controls.x;
    vec3 radiance = max(surface.rgb+halo.rgb,vec3(0.0));
    vec3 mapped = acesDisplayLinear(radiance*toneMapping.mapping.x);
    if (toneMapping.mapping.y > 0.5) mapped = encodeSrgb(mapped);
    float totalEnergy = dot(radiance,vec3(0.2126,0.7152,0.0722));
    float share = totalEnergy > 0.0 ? clamp(dot(surface.rgb,vec3(0.2126,0.7152,0.0722))/totalEnergy,0.0,1.0) : 0.0;
    // Encode the combined color once, then split; alpha remains coverage or numeric signal.
    result = toneMapping.controls.y > 0.5 ? vec4(mapped*(1.0-share),halo.a) : vec4(mapped*share,surface.a);
}
