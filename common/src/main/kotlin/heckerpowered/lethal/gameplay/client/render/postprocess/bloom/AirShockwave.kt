/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.lethal.gameplay.client.render.postprocess.bloom

import heckerpowered.bridge.adapter.client.render.context.WorldRenderContext
import heckerpowered.math.*
import heckerpowered.render.Float4
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.geometry.VertexRange
import heckerpowered.render.engine.image.RenderImage
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.TextureParameterValue
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.shader.parameter.*
import heckerpowered.render.engine.shader.program.FragmentOutput
import heckerpowered.render.engine.shader.program.VertexFragmentShader
import heckerpowered.render.geometry.Matrix4
import heckerpowered.render.memory.MemoryFrame
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.shader.ShaderEntryPoint
import kotlin.math.PI
import kotlin.math.exp

/** Fixed-radius ripples start first; the delayed expanding shell shares their background sample. */
data class AirShockwaveSettings(val strengthPixels: Float = 18F, val bandWidthFraction: Float = .12F, val radiusGrowthBlocks: Float = 10.5F, val expansionRatePerSecond: Float = 12F, val propagationRadiusScale: Float = 2F, val centerRadiusBlocks: Float = 3.5F, val centerStrengthPixels: Float = 4F, val centerPeriodSeconds: Float = .18F, val centerSpatialCycles: Float = 1.25F, val centerAttackSeconds: Float = .025F, val centerFadeStartSeconds: Float = .16F, val centerDurationSeconds: Float = .35F, val expansionDelaySeconds: Float = .06F, val expansionAttackSeconds: Float = .03F, val expansionFadeStartSeconds: Float = .10F, val expansionDurationSeconds: Float = .29F) {
    init {
        require(strengthPixels.isFinite() && strengthPixels in 0F..24F)
        require(bandWidthFraction.isFinite() && bandWidthFraction in 0.04F..0.16F)
        require(radiusGrowthBlocks.isFinite() && radiusGrowthBlocks in 0F..16F)
        require(expansionRatePerSecond.isFinite() && expansionRatePerSecond > 0F)
        require(propagationRadiusScale.isFinite() && propagationRadiusScale in 1F..2F)
        require(centerRadiusBlocks.isFinite() && centerRadiusBlocks > 0F)
        require(centerStrengthPixels.isFinite() && centerStrengthPixels in 0F..24F)
        require(centerPeriodSeconds.isFinite() && centerPeriodSeconds > 0F)
        require(centerSpatialCycles.isFinite() && centerSpatialCycles > 0F)
        require(expansionDelaySeconds.isFinite() && expansionDelaySeconds >= 0F)
        require(listOf(centerAttackSeconds, centerFadeStartSeconds, centerDurationSeconds, expansionAttackSeconds, expansionFadeStartSeconds, expansionDurationSeconds).all { it.isFinite() })
        require(centerAttackSeconds > 0F && centerFadeStartSeconds >= centerAttackSeconds && centerDurationSeconds > centerFadeStartSeconds)
        require(expansionAttackSeconds > 0F && expansionFadeStartSeconds >= expansionAttackSeconds && expansionDurationSeconds > expansionFadeStartSeconds)
    }

    val durationSeconds: Float get() = maxOf(centerDurationSeconds, expansionDelaySeconds + expansionDurationSeconds)
}

internal data class AirShockwaveSource(val center: VectorView, val ageSeconds: Float, val settings: AirShockwaveSettings = AirShockwaveSettings())

private const val MaximumAirShockwaves = 8
private const val AirShockwaveUniformBytes = 544

/**
 * Builds a camera-relative reconstruction in Double before packing the std140 column-major matrices.
 * The pass changes background RGB sampling only; it preserves destination alpha and emits no light.
 * It assumes a full-size world viewport, matching same-frame depth and no transparent-depth reconstruction.
 * Overlap sums bounded offsets, rather than tracing physically successive refractive interfaces.
 */
context(frame: MemoryFrame)
internal fun airShockwaveElement(program: VertexFragmentShader<AirShockwaveParameters>, context: WorldRenderContext, source: RenderImage, depth: TextureParameterValue, waves: List<AirShockwaveSource>): GeometryElement {
    require(waves.size <= MaximumAirShockwaves) { "An air shockwave pass supports at most eight sources" }
    require(source.size.width > 0 && source.size.height > 0)

    val camera = shockwaveCamera(context)
    for (wave in waves) {
        require(wave.center.isFinite() && wave.ageSeconds.isFinite() && wave.ageSeconds >= 0F)
    }

    val parameters = frame.allocAirShockwaveParameters()
    parameters.background = source.sampled()
    parameters.worldDepth = depth
    parameters.reconstruction(camera, source, waves.size)
    parameters.sources(waves, camera.position)
    return GeometryElement(program.bind(airShockwaveGeometry, parameters))
}

private class ShockwaveCamera(val position: VectorView, val relativeToClip: MatrixView, val clipToRelative: MatrixView, val depthDirection: Float)

private fun shockwaveCamera(context: WorldRenderContext): ShockwaveCamera {
    val position = context.cameraPosition.snapshot()
    require(position.isFinite()) { "Air shockwave camera must be finite" }

    val relativeToClip = context.viewProjectionMatrix.snapshot() * Matrices.fromTranslation(position)
    val clipToRelative = requireNotNull(relativeToClip.inverseOrNull()) { "Air shockwave projection must have a finite inverse" }
    val direction = when (context.depthCompare) {
        CompareFunction.Less, CompareFunction.LessOrEqual -> 1F
        CompareFunction.Greater, CompareFunction.GreaterOrEqual -> -1F
        else -> error("Air shockwave depth reconstruction requires an ordered depth comparison")
    }
    return ShockwaveCamera(position, relativeToClip, clipToRelative, direction)
}

context(frame: MemoryFrame)
private fun AirShockwaveParametersWriter.reconstruction(camera: ShockwaveCamera, source: RenderImage, count: Int) {
    requireShaderPrecision(camera.relativeToClip)
    requireShaderPrecision(camera.clipToRelative)

    relativeToClip(camera.relativeToClip)
    clipToRelative(camera.clipToRelative)
    viewport(source.size.width.toFloat(), source.size.height.toFloat(), count.toFloat(), camera.depthDirection)
    limits(24F, 0.35F, 0.03F, 0F)
}

private fun requireShaderPrecision(matrix: MatrixView) {
    for (column in 0..3) {
        for (row in 0..3) {
            require(matrix[row, column].toFloat().isFinite()) { "Air shockwave matrix exceeds shader precision" }
        }
    }
}

context(frame: MemoryFrame)
private fun AirShockwaveParametersWriter.sources(waves: List<AirShockwaveSource>, camera: VectorView) {
    repeat(MaximumAirShockwaves) { index ->
        val wave = waves.getOrNull(index)
        if (wave == null) {
            clearSource(index)
            return@repeat
        }

        source(index, wave, camera)
    }
}

context(frame: MemoryFrame)
private fun AirShockwaveParametersWriter.clearSource(index: Int) {
    center(index, 0F, 0F, 0F, 0F)
    wave(index, 0F, .12F, 0F, 0F)
    core(index, 1F, 0F, 0F, 1F)
}

context(frame: MemoryFrame)
private fun AirShockwaveParametersWriter.source(index: Int, source: AirShockwaveSource, camera: VectorView) {
    val settings = source.settings
    val age = source.ageSeconds
    val expansionAge = age - settings.expansionDelaySeconds
    val relative = source.center - camera
    require(listOf(relative.x, relative.y, relative.z).all { it.toFloat().isFinite() }) { "Air shockwave center exceeds camera-relative shader precision" }

    val radius = settings.propagationRadiusScale * (0.35F + settings.radiusGrowthBlocks * (1F - exp(-expansionAge.coerceAtLeast(0F) * settings.expansionRatePerSecond)))
    center(index, relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat(), radius)

    val expansionFade = airShockwaveTransition(0F, settings.expansionAttackSeconds, expansionAge) * (1F - airShockwaveTransition(settings.expansionFadeStartSeconds, settings.expansionDurationSeconds, expansionAge))
    wave(index, settings.strengthPixels * expansionFade, settings.bandWidthFraction, 0F, 0F)

    val centerFade = airShockwaveTransition(0F, settings.centerAttackSeconds, age) * (1F - airShockwaveTransition(settings.centerFadeStartSeconds, settings.centerDurationSeconds, age))
    core(index, settings.centerRadiusBlocks, settings.centerStrengthPixels * centerFade, (age / settings.centerPeriodSeconds) * (2.0 * PI).toFloat(), settings.centerSpatialCycles)
}

@ShaderParameters
internal interface AirShockwaveParameters {
    @SampledImage(AlphaQuantity.Coverage, AlphaRepresentation.Straight, anyAlphaQuantity = true, anyRepresentation = true)
    val background: TextureParameterValue

    @SampledImage(AlphaQuantity.Coverage, AlphaRepresentation.Straight, anyAlphaQuantity = true, anyRepresentation = true)
    val worldDepth: TextureParameterValue

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val relativeToClip: Matrix4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val clipToRelative: Matrix4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val viewport: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val limits: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val center0: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val center1: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val center2: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val center3: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val center4: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val center5: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val center6: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val center7: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val wave0: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val wave1: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val wave2: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val wave3: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val wave4: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val wave5: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val wave6: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val wave7: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val core0: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val core1: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val core2: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val core3: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val core4: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val core5: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val core6: Float4

    @UniformBlock("AirShockwave", AirShockwaveUniformBytes)
    val core7: Float4
}

private val airShockwaveGeometry = ShaderGeometry(GeometrySelection.Vertices(VertexRange(3)), PrimitiveState(PrimitiveTopology.TriangleList))

internal fun airShockwaveShader(): VertexFragmentShader<AirShockwaveParameters> = VertexFragmentShader("Local air shockwave refraction", AirShockwaveParameters::class, ShaderEntryPoint.classpath("/assets/render-engine/shaders/fullscreen.vert"), ShaderEntryPoint.classpath("/assets/lethal/shaders/postprocess/air_shockwave.frag"), colorOutput = FragmentOutput(AlphaRepresentation.Premultiplied, AlphaQuantity.Signal))

// The legacy OpenGL lowering requires named members instead of uniform-block arrays.
context(frame: MemoryFrame)
private fun AirShockwaveParametersWriter.center(index: Int, x: Float, y: Float, z: Float, w: Float) {
    when (index) {
        0 -> center0(x, y, z, w)
        1 -> center1(x, y, z, w)
        2 -> center2(x, y, z, w)
        3 -> center3(x, y, z, w)
        4 -> center4(x, y, z, w)
        5 -> center5(x, y, z, w)
        6 -> center6(x, y, z, w)
        7 -> center7(x, y, z, w)
        else -> error("Air shockwave index is out of range")
    }
}

context(frame: MemoryFrame)
private fun AirShockwaveParametersWriter.wave(index: Int, x: Float, y: Float, z: Float, w: Float) {
    when (index) {
        0 -> wave0(x, y, z, w)
        1 -> wave1(x, y, z, w)
        2 -> wave2(x, y, z, w)
        3 -> wave3(x, y, z, w)
        4 -> wave4(x, y, z, w)
        5 -> wave5(x, y, z, w)
        6 -> wave6(x, y, z, w)
        7 -> wave7(x, y, z, w)
        else -> error("Air shockwave index is out of range")
    }
}

context(frame: MemoryFrame)
private fun AirShockwaveParametersWriter.core(index: Int, x: Float, y: Float, z: Float, w: Float) {
    when (index) {
        0 -> core0(x, y, z, w)
        1 -> core1(x, y, z, w)
        2 -> core2(x, y, z, w)
        3 -> core3(x, y, z, w)
        4 -> core4(x, y, z, w)
        5 -> core5(x, y, z, w)
        6 -> core6(x, y, z, w)
        7 -> core7(x, y, z, w)
        else -> error("Air shockwave index is out of range")
    }
}

private fun airShockwaveTransition(start: Float, end: Float, value: Float): Float {
    val fraction = ((value - start) / (end - start)).coerceIn(0F, 1F)
    return fraction * fraction * (3F - 2F * fraction)
}
