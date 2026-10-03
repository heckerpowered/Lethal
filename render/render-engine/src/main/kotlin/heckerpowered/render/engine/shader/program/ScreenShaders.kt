/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.image.RenderImage
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.primitive.PrimitiveTopology

/** Defines the shipped sampled-copy shader; source is read on first device preparation. */
fun copyImageShader(): MeshShader<RenderImage> = fullscreenShader("copy") { source ->
    ParameterValues().replacing("source", source.sampled())
}

/** Defines the shipped tent-filter shader; source is read on first device preparation. */
fun tentShader(): MeshShader<RenderImage> = fullscreenShader("tent", field = "texelSize", components = 2) { source ->
    val texelSize = NumericParameterValue.floats(1f / source.size.width, 1f / source.size.height)
    ParameterValues().replacing("source", source.sampled()).replacing("texelSize", texelSize)
}

data class BrightnessInput(
    val source: RenderImage,
    val threshold: Float,
)

/** Defines the shipped brightness shader; source is read on first device preparation. */
fun brightnessShader(): MeshShader<BrightnessInput> =
    brightnessShader(AlphaQuantity.Coverage)

/** Preserves [alphaQuantity] without converting numeric Signal alpha into coverage. */
fun brightnessShader(alphaQuantity: AlphaQuantity): MeshShader<BrightnessInput> = fullscreenShader(
    "brightness",
    field = "threshold",
    output = FragmentOutput(AlphaRepresentation.Premultiplied, alphaQuantity),
) { [source, threshold] ->
    require(threshold.isFinite())
    require(source.sampled().sampledAlphaQuantity == alphaQuantity) { "Brightness extraction requires ${alphaQuantity.name.lowercase()} input" }
    ParameterValues().replacing("source", source.sampled()).replacing("threshold", NumericParameterValue.floats(threshold))
}

private val fullscreen = ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState(PrimitiveTopology.TriangleList))

private fun <P> fullscreenShader(effect: String, field: String? = null, components: Int = 1, output: FragmentOutput = FragmentOutput(AlphaRepresentation.Premultiplied, alphaFromTexture = ParameterName("source")), encode: (P) -> ParameterValues): MeshShader<P> {
    val definition = shaderDefinition(effect) {
        vertex("/assets/render-engine/shaders/fullscreen.vert")
        fragment("/assets/render-engine/shaders/$effect.frag")
        inputs { screenShaderInputs(this, field, components) }
        sourceRepresentation(AlphaRepresentation.Premultiplied)
        output(0, output)
        replaySafe()
    }
    return MeshShader(definition) { values -> MeshShaderInput(fullscreen, encode(values)) }
}
