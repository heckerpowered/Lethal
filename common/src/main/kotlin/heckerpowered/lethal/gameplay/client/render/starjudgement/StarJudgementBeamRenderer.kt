/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render.starjudgement

import heckerpowered.lethal.gameplay.client.render.postprocess.withWriteMask
import heckerpowered.math.VectorView
import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.VertexGeometry
import heckerpowered.render.engine.geometry.vertex.VertexSemantic
import heckerpowered.render.engine.geometry.vertex.VertexStreamSource
import heckerpowered.render.engine.geometry.vertex.geometryLayout
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.material.parameter.TextureParameterValue
import heckerpowered.render.engine.scene.ObjectRenderer
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderElementCollector
import heckerpowered.render.engine.scene.submit
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.shader.binding.shaderInputLayout
import heckerpowered.render.engine.shader.program.FragmentOutput
import heckerpowered.render.engine.shader.program.MeshShader
import heckerpowered.render.engine.shader.program.MeshShaderInput
import heckerpowered.render.engine.shader.program.shaderDefinition
import heckerpowered.render.pipeline.color.BlendState
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.color.ColorWriteMask
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.reflection.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Places the beam within the collector's local coordinate system, with its animation in game ticks. */
data class StarJudgementBeamDraw(val origin: VectorView, val animationTimeTicks: Double)

/**
 * Submits a rotating core and translucent shell using one immutable topology and two draws.
 *
 * The sampled texture must contain straight coverage color, with nearest filtering and repeat
 * addressing. Its owner keeps the texture and sampler alive through GPU completion. This CPU
 * renderer owns neither resource. [prepare] realizes the shader on the engine's device thread;
 * immutable topology storage is created by the engine on its first consuming stage.
 *
 * Each layer snapshots its corner, height and UV endpoints into 48 bytes. Time arithmetic and
 * local-origin rounding stay in Double until the final Float conversion, matching the legacy mesh.
 * Other collector placements are applied once by the engine. Static topology is shared across
 * captures; numeric snapshots remain independent when collected draws are replayed later.
 */
class StarJudgementBeamRenderer(texture: TextureParameterValue, colorWriteMask: ColorWriteMask = ColorWriteMask.All) : ObjectRenderer<StarJudgementBeamDraw> {
    init {
        require(texture.representation == AlphaRepresentation.Straight && texture.alphaQuantity == AlphaQuantity.Coverage) { "The beam requires straight coverage texture contents" }
    }

    private val coreComposition = CompositingMode.Replace.withWriteMask(colorWriteMask)
    private val shellComposition = BeamShellComposition.withWriteMask(colorWriteMask)

    private val coreAppearance = ParameterValues(
        ParameterName("texture") to texture,
        ParameterName("color") to NumericParameterValue.floats(1F, 1F, 1F, 1F),
    )
    private val shellAppearance = ParameterValues(
        ParameterName("texture") to texture,
        ParameterName("color") to NumericParameterValue.floats(1F, 1F, 1F, GLOW_ALPHA),
    )

    fun prepare(engine: RenderEngine) = engine.prepare(beamShader)

    context(context: ObjectSubmitContext)
    override fun submit(state: StarJudgementBeamDraw, collector: RenderElementCollector) {
        val scroll = -state.animationTimeTicks * 0.2 - floor(-state.animationTimeTicks * 0.1)
        val startV = -1.0 + (scroll - floor(scroll))
        val coreElement = beamShader.bind(coreAppearance.replacing("beamShape", coreShape(state.origin, state.animationTimeTicks, startV))) {
            composition(coreComposition)
            depthWrite(true)
        }
        val shellElement = beamShader.bind(shellAppearance.replacing("beamShape", shellShape(state.origin, startV))) {
            composition(shellComposition)
            depthWrite(false)
        }
        collector.submit(coreElement, shellElement)
    }
}

private fun coreShape(origin: VectorView, time: Double, startV: Double): NumericParameterValue {
    val rotation = time * -0.0375
    val first = rotation + Math.PI * 0.75
    val second = rotation + Math.PI * 0.25
    val third = rotation + Math.PI * 1.25
    val fourth = rotation + Math.PI * 1.75
    return NumericParameterValue.floats(
        (origin.x + (0.5 + cos(first) * INNER_RADIUS)).toFloat(), (origin.z + (0.5 + sin(first) * INNER_RADIUS)).toFloat(),
        (origin.x + (0.5 + cos(second) * INNER_RADIUS)).toFloat(), (origin.z + (0.5 + sin(second) * INNER_RADIUS)).toFloat(),
        (origin.x + (0.5 + cos(third) * INNER_RADIUS)).toFloat(), (origin.z + (0.5 + sin(third) * INNER_RADIUS)).toFloat(),
        (origin.x + (0.5 + cos(fourth) * INNER_RADIUS)).toFloat(), (origin.z + (0.5 + sin(fourth) * INNER_RADIUS)).toFloat(),
        (origin.y + BEAM_START_OFFSET).toFloat(), (origin.y + (BEAM_START_OFFSET + BEAM_HEIGHT)).toFloat(),
        startV.toFloat(), (startV + BEAM_HEIGHT * 0.5 / INNER_RADIUS).toFloat(),
    )
}

private fun shellShape(origin: VectorView, startV: Double): NumericParameterValue = NumericParameterValue.floats(
    (origin.x + (0.5 - GLOW_RADIUS)).toFloat(), (origin.z + (0.5 - GLOW_RADIUS)).toFloat(),
    (origin.x + (0.5 + GLOW_RADIUS)).toFloat(), (origin.z + (0.5 - GLOW_RADIUS)).toFloat(),
    (origin.x + (0.5 - GLOW_RADIUS)).toFloat(), (origin.z + (0.5 + GLOW_RADIUS)).toFloat(),
    (origin.x + (0.5 + GLOW_RADIUS)).toFloat(), (origin.z + (0.5 + GLOW_RADIUS)).toFloat(),
    (origin.y + BEAM_START_OFFSET).toFloat(), (origin.y + (BEAM_START_OFFSET + BEAM_HEIGHT)).toFloat(),
    startV.toFloat(), (startV + BEAM_HEIGHT).toFloat(),
)

private val beamVertexNumber = VertexSemantic("beamVertexNumber")
private val beamTopology = staticTopology()
private fun staticTopology(): VertexGeometry {
    val bytes = ByteBuffer.allocate(24 * 4).order(ByteOrder.nativeOrder())
    repeat(24) { bytes.putFloat(it.toFloat()) }
    val layout = geometryLayout(4) {
        attribute(beamVertexNumber, VertexFormat.Float32, 0)
    }
    return VertexGeometry(layout, listOf(VertexStreamSource.Static(bytes.array())), GeometrySelection.Vertices(DrawRange.vertices(24)), PrimitiveState(PrimitiveTopology.TriangleList))
}

private val beamShader = MeshShader<ParameterValues>(shaderDefinition("star judgement beam") {
    vertex("/assets/lethal/shaders/world/star_judgement_beam.vert")
    fragment("/assets/lethal/shaders/world/star_judgement_beam.frag")
    inputs { beamInputs(vertex, fragment) }
    output(0, FragmentOutput(AlphaRepresentation.Straight, AlphaQuantity.Coverage))
    sourceRepresentation(AlphaRepresentation.Straight)
    replaySafe()
}) { values -> MeshShaderInput(beamTopology, values) }

private fun beamInputs(vertex: ShaderInterfaceDescription, fragment: ShaderInterfaceDescription): ShaderInputLayout {
    val location = beamVertexLocation(vertex)
    val image = beamImage(fragment)
    requireBeamColorOutput(fragment)
    requireBeamPushBlock(vertex)
    requireBeamPushBlock(fragment)

    return shaderInputLayout {
        vertices {
            attribute(beamVertexNumber, location = location, format = VertexFormat.Float32)
        }
        descriptorSet(requireNotNull(image.set), ShaderStage.Fragment) {
            combinedTextureSampler(
                "texture",
                binding = requireNotNull(image.binding),
                alphaQuantity = AlphaQuantity.Coverage,
                representation = AlphaRepresentation.Straight,
            )
        }
        pushConstants(128, ShaderStage.Vertex, ShaderStage.Fragment) {
            parameter("clipFromLocal", offsetBytes = 0, sizeBytes = 64)
            parameter("beamShape", offsetBytes = 64, sizeBytes = 48)
            parameter("color", offsetBytes = 112, sizeBytes = 16)
        }
    }
}

private fun beamVertexLocation(vertex: ShaderInterfaceDescription): Int {
    val input = vertex.inputs.singleOrNull { it.name == "vertexNumber" }
    require(input != null && input.type.isFloatVector(1)) { "Beam vertexNumber must be one FP32 scalar" }

    val location = requireNotNull(input.location) { "Beam vertexNumber requires an explicit location" }
    require(location >= 0) { "Beam vertexNumber location must be nonnegative" }

    return location
}

private fun beamImage(fragment: ShaderInterfaceDescription): ShaderInterfaceResource {
    val resource = fragment.resources.singleOrNull { it.name == "image" && it.kind == ShaderInterfaceResourceKind.CombinedTextureSampler }
    requireNotNull(resource) { "Beam texture requires the named image resource" }
    val image = resource.image
    require(
        resource.descriptorCount == 1L && image != null && image.dimension == ShaderImageDimension.TwoDimensional &&
                !image.depth && !image.arrayed && !image.multisampled && image.sampledType.isFloatVector(1)
    ) { "Beam image must be one floating-point sampler2D" }
    return resource
}

private fun requireBeamColorOutput(fragment: ShaderInterfaceDescription) {
    val output = fragment.outputs.singleOrNull { it.location == 0 }
    require(output != null && output.type.isFloatVector(4)) { "Beam coverage color requires one FP32 vec4 at output zero" }
}

private fun requireBeamPushBlock(stage: ShaderInterfaceDescription) {
    val block = stage.resources.singleOrNull { it.kind == ShaderInterfaceResourceKind.PushConstant }
    require(block != null && block.sizeBytes == 128L) { "Beam parameters require a 128-byte push block" }
    val transform = block.members.singleOrNull { it.name == "clipFromLocal" }
    require(
        transform != null && transform.offsetBytes == 0 && transform.sizeBytes == 64L &&
                transform.type.scalar == ShaderScalarKind.Float && transform.type.bitWidth == 32 &&
                transform.type.components == 4 && transform.type.columns == 4 && transform.type.arrayDimensions.isEmpty() &&
                transform.matrixStrideBytes == 16 && transform.arrayStrideBytes == 0 && !transform.rowMajor
    ) { "Beam clipFromLocal requires a packed column-major FP32 mat4 at byte zero" }
    block.requireBeamVector("cornersA", 64)
    block.requireBeamVector("cornersB", 80)
    block.requireBeamVector("heightAndV", 96)
    block.requireBeamVector("color", 112)
}

private fun ShaderInterfaceResource.requireBeamVector(name: String, offsetBytes: Int) {
    val member = members.singleOrNull { it.name == name }
    require(
        member != null && member.offsetBytes == offsetBytes && member.sizeBytes == 16L &&
                member.type.isFloatVector(4) && member.matrixStrideBytes == 0 && member.arrayStrideBytes == 0 && !member.rowMajor
    ) { "Beam $name requires a packed FP32 vec4 at byte $offsetBytes" }
}

private fun ShaderValueDescription.isFloatVector(components: Int): Boolean =
    scalar == ShaderScalarKind.Float && bitWidth == 32 && this.components == components && columns == 1 && arrayDimensions.isEmpty()

/** The legacy core overwrites RGBA, so an imported host target has no inferred coverage contract. */
internal object BeamShellComposition : CompositingMode {
    override fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaQuantity?): ColorTargetState {
        require(source?.representation == AlphaRepresentation.Straight && source.alphaQuantity == AlphaQuantity.Coverage) { "The beam shell requires straight coverage samples" }
        return ColorTargetState(format, BlendState.StraightAlpha)
    }
}

private const val BEAM_START_OFFSET = -512.0
private const val BEAM_HEIGHT = 1024.0
private const val INNER_RADIUS = 0.2
private const val GLOW_RADIUS = 0.25
private const val GLOW_ALPHA = 0.125F
