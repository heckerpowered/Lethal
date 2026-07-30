/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.bridge.math.VectorView
import heckerpowered.lethal.Constants
import heckerpowered.render.*
import heckerpowered.render.VertexAttributeFormat.Float2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val BEAM_START_OFFSET = -512.0
private const val BEAM_HEIGHT = 1024.0
private const val INNER_RADIUS = 0.2
private const val GLOW_RADIUS = 0.25
private const val GLOW_ALPHA = 0.125F

private val VertexLayout = vertexBufferLayout { binding { attribute(Float2, "quadCoordinates") } }
private val SceneDescriptorLayout = descriptorSetLayout { uniformBuffer(0, "StarJudgementBeamScene", StarJudgementBeamSceneLayout, ShaderStage.Vertex) }
private val TextureDescriptorLayout = descriptorSetLayout { combinedImageSampler(0, "beamTexture", ShaderStage.Fragment) }
private val PushConstants = pushConstantLayout(StarJudgementBeamFaceConstantsLayout)
private val TextureSampler = SamplerDescription(TextureFilter.Nearest, TextureFilter.Nearest, SamplerAddressMode.Repeat, SamplerAddressMode.Repeat)
private val Shaders = ShaderProgram(
    ShaderProgramVariant(
        GraphicsBackend.OpenGL,
        ShaderSource.asset(Constants.identifier("world/star_judgement_beam_ubo.vsh")),
        ShaderSource.asset(Constants.identifier("world/star_judgement_beam.fsh")),
        GraphicsFeature.NativeUniformBuffers,
    ),
    ShaderProgramVariant(
        GraphicsBackend.OpenGL,
        ShaderSource.asset(Constants.identifier("world/star_judgement_beam.vsh")),
        ShaderSource.asset(Constants.identifier("world/star_judgement_beam.fsh")),
    ),
)
private val InnerPipeline = beamPipeline("Star Judgement beam core", BlendState.Disabled, DepthState.ReadWrite)
private val GlowPipeline = beamPipeline("Star Judgement beam glow", BlendState.StraightAlpha, DepthState.ReadOnly)

fun CommandEncoder.drawStarJudgementBeam(target: RenderTarget, modelViewProjectionMatrix: Matrix4, texture: GpuTexture, origin: VectorView, animationTimeTicks: Double) {
    val scene = writeStarJudgementBeamScene { this.modelViewProjectionMatrix = modelViewProjectionMatrix }
    val geometry = createStarJudgementBeamGeometry(animationTimeTicks)
    renderPass(RenderPassDescription("Star Judgement beam", target)) {
        bindVertexBuffer(0, primitives.unitQuad)
        drawFaces(InnerPipeline, scene, texture, origin, geometry.innerFaces)
        drawFaces(GlowPipeline, scene, texture, origin, geometry.glowFaces)
    }
}

private fun RenderPass.drawFaces(pipeline: RenderPipelineDescription, scene: UniformBinding, texture: GpuTexture, origin: VectorView, faces: List<StarJudgementBeamFace>) {
    bindPipeline(pipeline)
    bindDescriptorSet(0, SceneDescriptorLayout) { uniformBuffer(0, scene) }
    bindDescriptorSet(1, TextureDescriptorLayout) { combinedImageSampler(0, texture, TextureSampler) }

    for ((firstCorner, secondCorner, verticalRange, textureVRange, alpha) in faces) {
        pushStarJudgementBeamFaceConstants {
            beamOrigin(origin.x.toFloat(), origin.y.toFloat(), origin.z.toFloat())
            firstCorner(firstCorner.x.toFloat(), firstCorner.z.toFloat())
            secondCorner(secondCorner.x.toFloat(), secondCorner.z.toFloat())
            verticalRange(verticalRange.start.toFloat(), verticalRange.end.toFloat())
            textureVRange(textureVRange.start.toFloat(), textureVRange.end.toFloat())
            color(1.0F, 1.0F, 1.0F, alpha)
        }
        draw(4)
    }
}

internal fun createStarJudgementBeamGeometry(animationTimeTicks: Double): StarJudgementBeamGeometry {
    val textureOffset = fractionalPart(-animationTimeTicks * 0.2 - floor(-animationTimeTicks * 0.1))
    val rotation = animationTimeTicks * -0.0375
    val innerCorners = listOf(
        rotatedCorner(rotation + Math.PI * 0.75, INNER_RADIUS),
        rotatedCorner(rotation + Math.PI * 0.25, INNER_RADIUS),
        rotatedCorner(rotation + Math.PI * 1.25, INNER_RADIUS),
        rotatedCorner(rotation + Math.PI * 1.75, INNER_RADIUS),
    )
    val glowCorners = listOf(
        StarJudgementBeamCorner(0.5 - GLOW_RADIUS, 0.5 - GLOW_RADIUS),
        StarJudgementBeamCorner(0.5 + GLOW_RADIUS, 0.5 - GLOW_RADIUS),
        StarJudgementBeamCorner(0.5 - GLOW_RADIUS, 0.5 + GLOW_RADIUS),
        StarJudgementBeamCorner(0.5 + GLOW_RADIUS, 0.5 + GLOW_RADIUS),
    )
    val verticalRange = StarJudgementBeamRange(BEAM_START_OFFSET, BEAM_START_OFFSET + BEAM_HEIGHT)
    val innerTextureRange = StarJudgementBeamRange(-1.0 + textureOffset, -1.0 + textureOffset + BEAM_HEIGHT * 0.5 / INNER_RADIUS)
    val glowTextureRange = StarJudgementBeamRange(-1.0 + textureOffset, -1.0 + textureOffset + BEAM_HEIGHT)
    return StarJudgementBeamGeometry(
        faces(innerCorners, verticalRange, innerTextureRange, 1.0F),
        faces(glowCorners, verticalRange, glowTextureRange, GLOW_ALPHA),
    )
}

private fun beamPipeline(label: String, blendState: BlendState, depthState: DepthState): RenderPipelineDescription {
    return renderPipeline(label) {
        shaders(Shaders)
        vertexBuffer(VertexLayout)
        descriptorSet(0, SceneDescriptorLayout)
        descriptorSet(1, TextureDescriptorLayout)
        pushConstants(PushConstants)
        topology(PrimitiveTopology.TriangleStrip)
        blend(blendState)
        depth(depthState)
    }
}

private fun faces(corners: List<StarJudgementBeamCorner>, verticalRange: StarJudgementBeamRange, textureVRange: StarJudgementBeamRange, alpha: Float): List<StarJudgementBeamFace> {
    val (first, second, third, fourth) = corners
    return listOf(
        StarJudgementBeamFace(first, second, verticalRange, textureVRange, alpha),
        StarJudgementBeamFace(fourth, third, verticalRange, textureVRange, alpha),
        StarJudgementBeamFace(second, fourth, verticalRange, textureVRange, alpha),
        StarJudgementBeamFace(third, first, verticalRange, textureVRange, alpha),
    )
}

private fun rotatedCorner(angle: Double, radius: Double): StarJudgementBeamCorner {
    return StarJudgementBeamCorner(0.5 + cos(angle) * radius, 0.5 + sin(angle) * radius)
}

private fun fractionalPart(value: Double): Double {
    return value - floor(value)
}

internal data class StarJudgementBeamGeometry(
    val innerFaces: List<StarJudgementBeamFace>,
    val glowFaces: List<StarJudgementBeamFace>,
)

internal data class StarJudgementBeamFace(
    val firstCorner: StarJudgementBeamCorner,
    val secondCorner: StarJudgementBeamCorner,
    val verticalRange: StarJudgementBeamRange,
    val textureVRange: StarJudgementBeamRange,
    val alpha: Float,
)

internal data class StarJudgementBeamCorner(
    val x: Double,
    val z: Double,
)

internal data class StarJudgementBeamRange(
    val start: Double,
    val end: Double,
)
