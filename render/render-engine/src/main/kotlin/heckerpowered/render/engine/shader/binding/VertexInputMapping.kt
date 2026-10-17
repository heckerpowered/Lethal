/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.geometry.RenderGeometry
import heckerpowered.render.engine.geometry.VertexGeometry
import heckerpowered.render.engine.geometry.vertex.VertexStreamLayout
import heckerpowered.render.engine.support.collection.toUnmodifiableList
import heckerpowered.render.pipeline.vertex.VertexAttribute
import heckerpowered.render.pipeline.vertex.VertexBufferLayout
import heckerpowered.render.pipeline.vertex.VertexState

/**
 * Selects geometry attributes by semantic and assigns the locations read by a vertex shader.
 *
 * A position can be stored in any geometry stream without exposing that stream's layout to the
 * appearance. This mapping connects the position semantic to the shader's input declaration.
 * Shader reflection alone cannot determine an attribute's semantic.
 *
 * Input locations and semantics must each be unique. Every requested attribute must exist with
 * exactly the declared format. Unused geometry attributes are ignored, while stream numbering,
 * stride, stepping, and byte offsets are preserved. Generated-vertex geometry requires an empty
 * mapping because it supplies no vertex attributes.
 */
class VertexInputMapping(inputs: List<AttributeInput>) {
    val inputs = inputs.toUnmodifiableList()

    private val inputsBySemantic = this.inputs.associateBy { it.semantic }

    init {
        require(inputsBySemantic.size == this.inputs.size) { "Duplicate vertex input semantic" }
        require(this.inputs.map { it.location }.toSet().size == this.inputs.size) { "Duplicate vertex input location" }
    }

    fun lower(source: RenderGeometry): VertexState {
        if (source !is VertexGeometry) {
            require(inputs.isEmpty()) { "Shader-generated geometry cannot satisfy vertex attributes" }
            return VertexState.Empty
        }

        for ([semantic, _, format] in inputs) {
            val attribute = requireNotNull(source.layout.attribute(semantic)) { "Missing $semantic" }
            require(attribute.format == format) { "Unsupported format conversion for $semantic" }
        }

        return VertexState(source.layout.streams.map(::lowerStream))
    }

    private fun lowerStream(stream: VertexStreamLayout): VertexBufferLayout {
        val attributes = stream.attributes.mapNotNull { (semantic, format, offsetBytes) ->
            val location = inputsBySemantic[semantic]?.location ?: return@mapNotNull null
            VertexAttribute(location, format, offsetBytes)
        }

        return VertexBufferLayout(stream.strideBytes, stream.stepMode, attributes)
    }
}
