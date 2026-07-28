/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

enum class VertexAttributeFormat(val componentCount: Int, val sizeBytes: Int) {
    Float2(2, 8),
    Float3(3, 12),
    Float4(4, 16),
}

enum class VertexInputRate {
    Vertex,
    Instance,
}

data class VertexAttribute(
    val name: String,
    val location: Int,
    val format: VertexAttributeFormat,
    val offsetBytes: Int,
) {
    init {
        require(name.isNotBlank()) { "Vertex attribute name must not be blank" }
        require(location >= 0) { "Vertex attribute location must be non-negative" }
        require(offsetBytes >= 0) { "Vertex attribute offset must be non-negative" }
    }
}

data class VertexBufferBindingLayout(
    val strideBytes: Int,
    val inputRate: VertexInputRate,
    val attributes: List<VertexAttribute>,
) {
    init {
        require(strideBytes > 0) { "Vertex stride must be positive" }
        require(attributes.isNotEmpty()) { "Vertex buffer binding requires at least one attribute" }
        require(attributes.distinctBy(VertexAttribute::location).size == attributes.size) { "Vertex attribute locations must be unique" }
        require(attributes.all { it.offsetBytes + it.format.sizeBytes <= strideBytes }) { "Vertex attributes must fit inside the vertex stride" }
    }
}

data class VertexBufferLayout(val bindings: List<VertexBufferBindingLayout>) {
    init {
        val attributes = bindings.flatMap(VertexBufferBindingLayout::attributes)
        require(attributes.distinctBy(VertexAttribute::location).size == attributes.size) { "Vertex attribute locations must be unique across every buffer binding" }
        require(attributes.distinctBy(VertexAttribute::name).size == attributes.size) { "Vertex attribute names must be unique across every buffer binding" }
    }

    companion object {
        val Empty = VertexBufferLayout(emptyList())
    }
}

/**
 * Builds vertex-buffer bindings in slot order and assigns attribute locations across the complete layout.
 */
class VertexBufferLayoutBuilder internal constructor() {
    private val bindings = mutableListOf<VertexBufferBindingLayout>()
    private var nextAttributeLocation = 0

    fun binding(inputRate: VertexInputRate = VertexInputRate.Vertex, attributes: VertexBufferBindingLayoutBuilder.() -> Unit) {
        val binding = VertexBufferBindingLayoutBuilder(nextAttributeLocation).apply(attributes).build(inputRate)
        bindings += binding
        nextAttributeLocation += binding.attributes.size
    }

    internal fun build() = VertexBufferLayout(bindings.toList())
}

/**
 * Assigns each attribute's byte offset and derives the final binding stride.
 */
class VertexBufferBindingLayoutBuilder internal constructor(private val firstAttributeLocation: Int) {
    private val attributes = mutableListOf<VertexAttribute>()
    private var strideBytes = 0

    fun attribute(format: VertexAttributeFormat, name: String) {
        attributes += VertexAttribute(name, firstAttributeLocation + attributes.size, format, strideBytes)
        strideBytes = Math.addExact(strideBytes, format.sizeBytes)
    }

    fun padding(byteCount: Int) {
        require(byteCount > 0) { "Vertex binding padding must be positive" }
        strideBytes = Math.addExact(strideBytes, byteCount)
    }

    internal fun build(inputRate: VertexInputRate): VertexBufferBindingLayout {
        return VertexBufferBindingLayout(strideBytes, inputRate, attributes.toList())
    }
}

/**
 * Declares a vertex layout while deriving binding slots, attribute locations, offsets, and strides from source order.
 */
fun vertexBufferLayout(bindings: VertexBufferLayoutBuilder.() -> Unit): VertexBufferLayout {
    return VertexBufferLayoutBuilder().apply(bindings).build()
}
