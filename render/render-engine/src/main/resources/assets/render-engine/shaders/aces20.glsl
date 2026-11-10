/*
 * Copyright Contributors to the OpenColorIO Project.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

float acesReachAtHue(float h) {
    float i_base = floor(h);
    float i_lo = i_base + float(1);
    float i_hi = i_lo + 1.0;
    float lo = acesLookup(int(i_lo)).a;
    float hi = acesLookup(int(i_hi)).a;
    float t = h - i_base;

    return mix(lo, hi, t);
}

float acesToneScale(float J) {
    float response = acesWhiteResponse * pow(abs(J) * 0.01, acesInverseModelGamma);
    float Y = pow(27.13 * response / (1.0 - response), 1.0 / 0.42) / acesLuminanceAdaptation;
    float f = acesToneM2 * pow(Y / (Y + acesToneS2), acesToneGamma);
    float mappedY = max(0.0, f * f / (f + acesToneToe)) * acesReferenceLuminance;
    float adapted = pow(mappedY * acesLuminanceAdaptation, 0.42);
    float mappedJ = 100.0 * pow(adapted / (27.13 + adapted) * acesInverseWhiteResponse, acesModelGamma);
    return sign(J) * mappedJ;
}

float acesToe(float x, float limit, float k1_in, float k2_in) {
    float k2 = max(k2_in, 0.001);
    float k1 = sqrt(k1_in * k1_in + k2 * k2);
    float k3 = (limit + k1) / (limit + k2);

    return (x > limit) ? x : 0.5 * (k3 * x - k1 + sqrt((k3 * x - k1) * (k3 * x - k1) + 4.0 * k2 * k3 * x));
}


vec3 acesCuspAtHue(float h) {
    int i = int(h) + 1;
    int i_lo = int(max(float(0), float(i + acesHueSearchLow)));
    int i_hi = int(min(float(361), float(i + acesHueSearchHigh)));

    while (i_lo + 1 < i_hi) {
        float hcur = acesCuspHues[i];

        if (h > hcur) {
            i_lo = i;
        } else {
            i_hi = i;
        }

        i = (i_lo + i_hi) / 2;
    }

    vec3 lo = acesLookup(i_hi - 1).rgb;
    vec3 hi = acesLookup(i_hi).rgb;
    float t = (h - acesCuspHues[i_hi - 1]) / (acesCuspHues[i_hi] - acesCuspHues[i_hi - 1]);

    return mix(lo, hi, t);
}

float acesFocusGain(float J, float cuspJ) {
    float thr = mix(cuspJ, acesLimitJ, 0.300000);

    if (J > thr) {
        float gain = (acesLimitJ - thr) / max(0.0001, acesLimitJ - J);
        gain = log(gain) / log(10.0);

        return gain * gain + 1.0;
    } else {
        return 1.0;
    }
}

float acesIntersectLightness(float J, float M, float focusJ, float slope_gain) {
    float M_scaled = M / slope_gain;
    float a = M_scaled / focusJ;

    if (J < focusJ) {
        float b = 1.0 - M_scaled;
        float c = -J;
        float det = b * b - 4.f * a * c;
        float root = sqrt(det);

        return -2.0 * c / (b + root);
    } else {
        float b = -(1.0 + M_scaled + acesLimitJ * a);
        float c = acesLimitJ * M_scaled + J;
        float det = b * b - 4.f * a * c;
        float root = sqrt(det);

        return -2.0 * c / (b - root);
    }
}

float acesIntersectGamut(vec2 JM_cusp, float gamma_top_inv, float gamma_bottom_inv, float J_intersect_source, float J_intersect_cusp, float slope) {
    float M_boundary_lower = J_intersect_cusp * pow(J_intersect_source / J_intersect_cusp, gamma_bottom_inv) / (JM_cusp.r / JM_cusp.g - slope);
    float M_boundary_upper = JM_cusp.g * (acesLimitJ - J_intersect_cusp) * pow((acesLimitJ - J_intersect_source) / (acesLimitJ - J_intersect_cusp), gamma_top_inv) / (slope * JM_cusp.g + acesLimitJ - JM_cusp.r);
    float smin = 0.0;

    {
        float a = M_boundary_lower;
        float b = M_boundary_upper;
        float s = 0.119999997 * JM_cusp.g;
        float h = max(s - abs(a - b), 0.0) / s;
        smin = min(a, b) - h * h * h * s * 0.16666666666666666;
    }

    return smin;
}

float acesRemapChroma(float M, float gamut_boundary_M, float reach_boundary_M) {
    float boundary_ratio = gamut_boundary_M / reach_boundary_M;
    float proportion = max(boundary_ratio, 0.75);
    float threshold = proportion * gamut_boundary_M;

    if (proportion >= 1.0f || M <= threshold) {
        return M;
    }

    float m_offset = M - threshold;
    float gamut_offset = gamut_boundary_M - threshold;
    float reach_offset = reach_boundary_M - threshold;
    float scale = reach_offset / ((reach_offset / gamut_offset) - 1.0f);
    float nd = m_offset / scale;

    return threshold + scale * nd / (1.0f + nd);
}

vec3 acesCompressGamut(vec3 JMh, float Jx, vec3 JMGcusp, float reachMaxM) {
    float J = JMh.r;
    float M = JMh.g;
    float h = JMh.b;

    if (J <= 0.0)
    return vec3(0.0, 0.0, h);

    if (M <= 0.0 || J > acesLimitJ) {
        return vec3(J, 0.0, h);
    } else {
        vec2 JMcusp = JMGcusp.rg;
        float focusJ = mix(JMcusp.r, acesMidJ, min(1.0, 1.300000 - (JMcusp.r / acesLimitJ)));
        float slope_gain = (acesLimitJ * acesFocusDistance) * acesFocusGain(Jx, JMcusp.r);
        float J_intersect_source = acesIntersectLightness(JMh.r, JMh.g, focusJ, slope_gain);
        float gamut_slope = (J_intersect_source < focusJ) ? J_intersect_source : (acesLimitJ - J_intersect_source);
        gamut_slope = gamut_slope * (J_intersect_source - focusJ) / (focusJ * slope_gain);
        float gamma_top_inv = JMGcusp.b;
        float gamma_bottom_inv = acesLowerHullGammaInverse;
        float J_intersect_cusp = acesIntersectLightness(JMcusp.r, JMcusp.g, focusJ, slope_gain);
        float gamutBoundaryM = acesIntersectGamut(JMcusp, gamma_top_inv, gamma_bottom_inv, J_intersect_source, J_intersect_cusp, gamut_slope);

        if (gamutBoundaryM <= 0.0) {
            return vec3(J, 0.0, h);
        }

        float reachBoundaryM = acesLimitJ * pow(J_intersect_source / acesLimitJ, acesInverseModelGamma);
        reachBoundaryM = reachBoundaryM / ((acesLimitJ / reachMaxM) - gamut_slope);
        float remapped_M = acesRemapChroma(M, gamutBoundaryM, reachBoundaryM);
        float remapped_J = J_intersect_source + remapped_M * gamut_slope;

        return vec3(remapped_J, remapped_M, h);
    }
}

// Academy OutputTransform clamps in AP1 using the tone scale's forward limit.
const mat3 srgbToAp1 = mat3(
0.61309740240118771, 0.070193722469581762, 0.020615592882226922,
0.33952314618410584, 0.91635387905734433, 0.10956977293813543,
0.047379451414706737, 0.01345239847307418, 0.86981463417963745
);
const mat3 ap1ToAp0 = mat3(
0.69545224135745176, 0.044794563372037646, -0.0055258825581135443,
0.14067869647029416, 0.85967111845642163, 0.0040252103059786586,
0.16386906217225403, 0.095534318171540358, 1.0015006722521349
);

vec3 acesRgbToAppearance(vec3 rgb) {
    vec3 cone = acesInputToCone * rgb;
    vec3 adapted = pow(abs(cone), vec3(0.42));
    vec3 opponent = acesConeToOpponent * (sign(cone) * adapted / (27.13 + adapted));
    if (opponent.x <= 0.0)
    return vec3(0.0);

    float lightness = 100.0 * pow(opponent.x, acesModelGamma);
    float colorfulness = length(opponent.yz);
    float hue = all(equal(opponent.yz, vec2(0.0))) ? 0.0 : degrees(atan(opponent.z, opponent.y));
    return vec3(lightness, colorfulness, mod(hue, 360.0));
}

vec3 acesAppearanceToRgb(vec3 appearance) {
    float hue = radians(appearance.z);
    vec3 opponent = vec3(
    pow(appearance.x * 0.01, acesInverseModelGamma),
    appearance.y * cos(hue),
    appearance.y * sin(hue)
    );
    vec3 response = acesOpponentToCone * opponent;
    vec3 magnitude = min(abs(response), vec3(0.99));
    vec3 cone = sign(response) * pow(27.13 * magnitude / (1.0 - magnitude), vec3(1.0 / 0.42));
    return acesConeToOutput * cone;
}

float acesChromaNormalization(float hue) {
    float cosine = cos(radians(hue));
    float sine = sin(radians(hue));
    vec3 cosines = vec3(cosine, 2.0 * cosine * cosine - 1.0, 4.0 * cosine * cosine * cosine - 3.0 * cosine);
    vec3 sines = vec3(sine, 2.0 * cosine * sine, 3.0 * sine - 4.0 * sine * sine * sine);
    return dot(cosines, acesChromaCosines) + dot(sines, acesChromaSines) + acesChromaConstant;
}

vec3 acesCompressChroma(vec3 appearance, float mappedLightness, float reachMax) {
    if (appearance.y == 0.0)
    return vec3(mappedLightness, 0.0, appearance.z);

    float normalizedLightness = mappedLightness / acesLimitJ;
    float remainingLightness = max(0.0, 1.0 - normalizedLightness);
    float normalization = acesChromaNormalization(appearance.z);
    float limit = pow(normalizedLightness, acesInverseModelGamma) * reachMax / normalization;
    float chroma = appearance.y * pow(mappedLightness / appearance.x, acesInverseModelGamma) / normalization;
    chroma = limit - acesToe(
        limit - chroma, limit - 0.001,
        remainingLightness * acesChromaSaturation,
        sqrt(normalizedLightness * normalizedLightness + acesChromaSaturationThreshold)
    );
    chroma = acesToe(chroma, limit, normalizedLightness * acesChromaCompression, remainingLightness);
    return vec3(mappedLightness, chroma * normalization, appearance.z);
}

// Display-linear limiting RGB, relative to 100 nits rather than the configured peak.
vec4 aces2DisplayLinear(vec4 inputColor) {
    vec3 ap0 = ap1ToAp0 * clamp(srgbToAp1 * inputColor.rgb, vec3(0.0), vec3(acesInputLimit));
    vec3 appearance = acesRgbToAppearance(ap0);
    float reachMax = acesReachAtHue(appearance.z);
    float lightness = acesToneScale(appearance.x);
    appearance = acesCompressChroma(appearance, lightness, reachMax);
    appearance = acesCompressGamut(appearance, appearance.x, acesCuspAtHue(appearance.z), reachMax);
    vec3 outputColor = acesAppearanceToRgb(appearance);
    // OCIO output adapter clips after rendering; this is separate from gamut compression.
    return vec4(clamp(outputColor, vec3(0.0), vec3(acesPeakRelative)), inputColor.a);
}
