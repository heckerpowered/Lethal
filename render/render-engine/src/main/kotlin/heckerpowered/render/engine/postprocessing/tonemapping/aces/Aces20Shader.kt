/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing.tonemapping.aces

import heckerpowered.render.engine.shader.buildShader
import heckerpowered.render.io.resource

/**
 * Emits shader constants and hue data from the same output-transform parameters.
 * The hue-dependent AP1 cusp fit is scale × (a0 + Σₙ(an × cos(n × h) + bn × sin(n × h)))
 * for n=1..3. Its fitted coefficients are scaled here once, not once per pixel.
 * Array and texture paths share the same cusp/reach values and mathematical shader body.
 */
internal fun Aces20Parameters.shaderSource(lookup: Boolean): String = buildShader {
    val hueLookup = gamut.lookup

    matrix("acesInputToCone", inputAppearance.rgbToCone)
    matrix("acesConeToOpponent", inputAppearance.coneToOpponent)
    matrix("acesOpponentToCone", limitingAppearance.opponentToCone)
    matrix("acesConeToOutput", limitingAppearance.coneToRgb)
    scalar("acesModelGamma", inputAppearance.modelGamma)
    scalar("acesInverseModelGamma", inputAppearance.inverseModelGamma)
    scalar("acesLuminanceAdaptation", inputAppearance.luminanceAdaptation)
    scalar("acesWhiteResponse", inputAppearance.whiteResponse)
    scalar("acesInverseWhiteResponse", inputAppearance.inverseWhiteResponse)
    scalar("acesInputLimit", toneScale.forwardLimit)
    scalar("acesToneM2", toneScale.m2)
    scalar("acesToneS2", toneScale.s2)
    scalar("acesToneGamma", toneScale.g)
    scalar("acesToneToe", toneScale.t1)
    scalar("acesReferenceLuminance", toneScale.nr)
    // RGB=1 represents the fixed reference luminance, not this display's peak.
    scalar("acesPeakRelative", toneScale.n / toneScale.nr)
    scalar("acesLimitJ", gamut.limitJMax)
    scalar("acesMidJ", gamut.midJ)
    scalar("acesFocusDistance", gamut.focusDistance)
    scalar("acesLowerHullGammaInverse", gamut.lowerHullGammaInverse)
    scalar("acesChromaSaturation", chroma.saturation)
    scalar("acesChromaSaturationThreshold", chroma.saturationThreshold)
    scalar("acesChromaCompression", chroma.compression)
    vector("acesChromaCosines", listOf(11.34072f, 16.46899f, 7.88380f).map { it * chroma.scale })
    vector("acesChromaSines", listOf(14.66441f, -6.37224f, 9.19364f).map { it * chroma.scale })
    scalar("acesChromaConstant", 77.12896f * chroma.scale)
    scalar("acesHueSearchLow", hueLookup.searchLow)
    scalar("acesHueSearchHigh", hueLookup.searchHigh)
    appendLine("const float acesCuspHues[${hueLookup.size}] = float[${hueLookup.size}](")
    appendLine(List(hueLookup.size) { hueLookup.hue(it) }.chunked(6).joinToString(",\n") { row -> "    " + row.joinToString(", ") { it.glsl() } })
    appendLine(");")
    if (!lookup) {
        appendLine("const vec4 acesHueData[${hueLookup.size}] = vec4[${hueLookup.size}](")
        appendLine((0 until hueLookup.size).joinToString(",\n") { index ->
            val values = List(3) { hueLookup.cusp(index, it) } + hueLookup.reach(index)
            "    vec4(${values.joinToString(", ") { it.glsl() }})"
        })
        appendLine(");")
        appendLine("vec4 acesLookup(int index) { return acesHueData[index]; }")
    }
    append(resource("/assets/render-engine/shaders/aces20.glsl").bufferedReader().use { it.readText() })
}

private fun Float.glsl(): String = toString()
