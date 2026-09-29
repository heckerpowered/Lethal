/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.vertex

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class VertexLayoutDsl

fun vertexState(block: VertexStateBuilder.() -> Unit): VertexState =
    VertexStateBuilder().apply(block).build()

fun vertexState(stride: Int, stepMode: VertexStepMode = VertexStepMode.Vertex, block: VertexBufferLayoutBuilder.() -> Unit): VertexState {
    return vertexState { buffer(stride, stepMode, block) }
}

@VertexLayoutDsl
class VertexStateBuilder internal constructor() {
    private val buffers = mutableListOf<VertexBufferLayout>()

    fun buffer(stride: Int, stepMode: VertexStepMode = VertexStepMode.Vertex, block: VertexBufferLayoutBuilder.() -> Unit) {
        buffers += VertexBufferLayoutBuilder(stride, stepMode).apply(block).build()
    }

    internal fun build(): VertexState = VertexState(buffers.toList())
}

@VertexLayoutDsl
class VertexBufferLayoutBuilder internal constructor(
    private val stride: Int,
    private val stepMode: VertexStepMode,
) {
    private val attributes = mutableListOf<VertexAttribute>()

    fun attribute(location: Int, format: VertexFormat, offset: Int) {
        attributes += VertexAttribute(location, format, offset)
    }

    internal fun build(): VertexBufferLayout = VertexBufferLayout(stride, stepMode, attributes.toList())
}
