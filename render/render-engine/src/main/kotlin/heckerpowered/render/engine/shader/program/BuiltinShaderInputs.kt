/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.engine.geometry.vertex.VertexSemantic
import heckerpowered.render.engine.geometry.vertex.VertexSemantics
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.shader.binding.*
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.binding.DescriptorBindingLayout
import heckerpowered.render.shader.binding.DescriptorSetLayout
import heckerpowered.render.shader.binding.DescriptorType
import heckerpowered.render.shader.reflection.*

internal fun screenShaderInputs(reflection: RasterShaderInterface, field: String? = null, components: Int = 1): ShaderInputLayout {
    val vertex = reflection.vertex
    val fragment = reflection.fragment
    val descriptors = sampledImageInputs(fragment, "source", null, null)
    val pushes = screenPushConstants(fragment, field, components)

    require(vertex.inputs.isEmpty() && vertex.resources.isEmpty()) { "Fullscreen shaders require generated vertices and no vertex resources" }
    validateCoordinates(vertex, fragment)
    validateColorOutput(fragment)
    require(fragment.resources.size == 1 + pushes.size) { "Unexpected screen resources" }
    return ShaderInputLayout(VertexInputMapping(emptyList()), descriptors, pushes)
}

internal fun surfaceShaderInputs(reflection: RasterShaderInterface, representation: AlphaRepresentation?): ShaderInputLayout {
    val vertex = reflection.vertex
    val fragment = reflection.fragment
    val textured = representation != null
    val vertices = surfaceVertexInputs(vertex, textured)
    val descriptors = if (textured) sampledImageInputs(fragment, "texture", AlphaQuantity.Coverage, representation) else emptyList()
    val push = surfacePushConstants(reflection.stageFacts)

    validateSurfaceVaryings(vertex, fragment, textured)
    validateColorOutput(fragment)
    require(vertex.resources.size == 1 && fragment.resources.size == if (textured) 2 else 1) { "Unexpected surface resources" }
    return ShaderInputLayout(vertices, descriptors, listOf(push))
}

private fun surfaceVertexInputs(vertex: ShaderInterfaceDescription, textured: Boolean): VertexInputMapping {
    require(vertex.inputs.size == if (textured) 2 else 1) { "Unexpected surface vertex attributes" }
    val position = vertex.attribute("position", VertexSemantics.Position, VertexFormat.Float32x3, 3)
    val coordinates = if (textured) vertex.attribute("uv", VertexSemantics.UV, VertexFormat.Float32x2, 2) else null
    return VertexInputMapping(listOfNotNull(position, coordinates))
}

private fun ShaderInterfaceDescription.attribute(name: String, semantic: VertexSemantic, format: VertexFormat, components: Int): AttributeInput {
    val attribute = inputs.singleOrNull { it.name == name }
    require(attribute != null && attribute.type.isFloatVector(components)) { "Unsupported surface attribute: $name" }
    val location = attribute.location
    require(location != null && location >= 0) { "Surface attribute requires an explicit location: $name" }
    return AttributeInput(semantic, location, format)
}

private fun sampledImageInputs(fragment: ShaderInterfaceDescription, parameter: String, quantity: AlphaQuantity?, representation: AlphaRepresentation?): List<DescriptorInputMapping> {
    val image = fragment.resources.singleOrNull { it.kind == ShaderInterfaceResourceKind.CombinedTextureSampler }
    require(image != null && image.isFloatSampler2D()) { "Builtin image input requires one floating-point sampler2D" }
    val set = image.set
    val binding = image.binding
    require(set != null && binding != null && set >= 0 && binding >= 0) { "Image input requires explicit descriptor slots" }

    val layout = DescriptorSetLayout(
        listOf(DescriptorBindingLayout(binding, DescriptorType.CombinedTextureSampler(), stages = setOf(ShaderStage.Fragment))),
        "builtin image",
    )
    val input = DescriptorInputMapping(layout, listOf(DescriptorParameter(ParameterName(parameter), binding, alphaQuantity = quantity, representation = representation)))
    return List(set + 1) { index ->
        if (index == set) input else DescriptorInputMapping(DescriptorSetLayout(emptyList(), "unused builtin set"), emptyList())
    }
}

private fun screenPushConstants(fragment: ShaderInterfaceDescription, field: String?, components: Int): List<PushConstantPacking> {
    val blocks = fragment.resources.filter { it.kind == ShaderInterfaceResourceKind.PushConstant }
    require(blocks.size == if (field == null) 0 else 1) { "Unexpected screen push-constant blocks" }
    if (field == null) return emptyList()

    val block = blocks.single()
    val member = block.members.singleOrNull { it.name == field }
    require(member != null && block.members.size == 1 && member.isPackedFloatVector(components)) { "Unsupported screen parameter shape" }
    val packing = PushConstantPacking(setOf(ShaderStage.Fragment), 0, block.blockSize(), listOf(member.pushConstantField()))
    return listOf(packing)
}

private fun surfacePushConstants(facts: Map<ShaderStage, ShaderInterfaceDescription>): PushConstantPacking {
    val blocks = facts.map { [stage, description] ->
        val block = description.resources.singleOrNull { it.kind == ShaderInterfaceResourceKind.PushConstant }
        require(block != null && block.members.size == 2) { "Surface stages require the declared transform/color block" }
        stage to block
    }
    val block = blocks.first().second
    val transform = block.members.singleOrNull { it.name == "clipFromLocal" }
    val color = block.members.singleOrNull { it.name == "color" }
    require(transform != null && transform.isColumnMajorFloatMatrix4()) { "Surface transform must be a column-major float mat4" }
    require(color != null && color.isPackedFloatVector(4)) { "Surface color must be a float vec4" }
    val blockSize = block.blockSize()
    for ([_, other] in blocks.drop(1)) {
        other.requireSamePushLayout(block, blockSize)
    }
    return PushConstantPacking(
        blocks.map { it.first }.toSet(),
        0,
        blockSize,
        listOf(transform.pushConstantField(), color.pushConstantField()),
    )
}

private fun ShaderInterfaceResource.requireSamePushLayout(expected: ShaderInterfaceResource, expectedSize: Int) {
    require(blockSize() == expectedSize) { "Surface stages disagree about push block extent" }
    for (member in members) {
        val expectedMember = expected.members.singleOrNull { it.name == member.name }
        require(expectedMember != null && member.sameLayout(expectedMember)) { "Surface stages disagree about push member layout" }
    }
}

private fun ShaderInterfaceBlockMember.pushConstantField(): PushConstantField =
    PushConstantField(ParameterName(name), offsetBytes, sizeBytes.toInt())

private fun validateSurfaceVaryings(vertex: ShaderInterfaceDescription, fragment: ShaderInterfaceDescription, textured: Boolean) {
    if (textured) {
        validateCoordinates(vertex, fragment)
        return
    }
    require(vertex.outputs.isEmpty() && fragment.inputs.isEmpty()) { "Unlit surfaces require no varyings" }
}

private fun validateCoordinates(vertex: ShaderInterfaceDescription, fragment: ShaderInterfaceDescription) {
    require(vertex.outputs.size == 1 && fragment.inputs.size == 1) { "Builtin sampling requires one coordinate varying" }
    val coordinates = vertex.outputs.single()
    val sampledCoordinates = fragment.inputs.single()
    val sameLocation = coordinates.location != null && coordinates.location == sampledCoordinates.location
    require(sameLocation && coordinates.type.isFloatVector(2) && sampledCoordinates.type.isFloatVector(2)) { "Coordinate varying interfaces disagree" }
}

private fun validateColorOutput(fragment: ShaderInterfaceDescription) {
    val output = fragment.outputs.singleOrNull()
    require(output != null && output.location == 0 && output.type.isFloatVector(4)) { "Builtin shaders require one float vec4 color output at location zero" }
}

private fun ShaderInterfaceResource.blockSize(): Int {
    val size = sizeBytes
    require(size != null && size > 0 && size <= Int.MAX_VALUE && size % 4 == 0L) { "Unsupported push block extent" }
    return size.toInt()
}

private fun ShaderInterfaceResource.isFloatSampler2D(): Boolean {
    val image = image ?: return false
    return name == "image" && descriptorCount == 1L &&
            image.dimension == ShaderImageDimension.TwoDimensional &&
            !image.depth && !image.arrayed && !image.multisampled &&
            image.sampledType.isFloatVector(1)
}

private fun ShaderInterfaceBlockMember.isPackedFloatVector(components: Int): Boolean =
    type.isFloatVector(components) &&
            matrixStrideBytes == 0 && arrayStrideBytes == 0 && !rowMajor &&
            sizeBytes == components.toLong() * Float.SIZE_BYTES

private fun ShaderInterfaceBlockMember.isColumnMajorFloatMatrix4(): Boolean =
    type.scalar == ShaderScalarKind.Float && type.bitWidth == 32 &&
            type.components == 4 && type.columns == 4 && type.arrayDimensions.isEmpty() &&
            matrixStrideBytes == 4 * Float.SIZE_BYTES && arrayStrideBytes == 0 && !rowMajor &&
            sizeBytes == 16L * Float.SIZE_BYTES

private fun ShaderInterfaceBlockMember.sameLayout(other: ShaderInterfaceBlockMember): Boolean =
    offsetBytes == other.offsetBytes && sizeBytes == other.sizeBytes &&
            matrixStrideBytes == other.matrixStrideBytes && arrayStrideBytes == other.arrayStrideBytes &&
            rowMajor == other.rowMajor && type.sameShape(other.type)

private fun ShaderValueDescription.isFloatVector(count: Int): Boolean =
    scalar == ShaderScalarKind.Float && bitWidth == 32 &&
            components == count && columns == 1 && arrayDimensions.isEmpty()

private fun ShaderValueDescription.sameShape(other: ShaderValueDescription): Boolean =
    scalar == other.scalar && bitWidth == other.bitWidth &&
            components == other.components && columns == other.columns && arrayDimensions == other.arrayDimensions
