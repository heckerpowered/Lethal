/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.memory.FloatElements
import heckerpowered.render.memory.IntElements
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.memory.NativeStruct

/**
 * Keeps the three query results alive together until the clear state is restored.
 *
 * KSP generates withOpenGLClearState: one reservation contains all three records. Child views
 * expose both named components for restoration and bounded NIO buffers for the native queries.
 */
@NativeStruct
internal interface OpenGLClearState {
    val scissor: OpenGLScissorState
    val colorWriteMask: OpenGLColorWriteMaskState
    val clearColor: OpenGLClearColorState
}

@NativeStruct
internal interface OpenGLScissorState {
    val x: Int
    val y: Int
    val width: Int
    val height: Int

    // LWJGL 2 checks capacity for sixteen elements even when this query writes only four.
    @IntElements(12)
    val queryCapacity: NativeAddress
}

@NativeStruct
internal interface OpenGLColorWriteMaskState {
    val red: Int
    val green: Int
    val blue: Int
    val alpha: Int

    @IntElements(12)
    val queryCapacity: NativeAddress
}

@NativeStruct
internal interface OpenGLClearColorState {
    val red: Float
    val green: Float
    val blue: Float
    val alpha: Float

    @FloatElements(12)
    val queryCapacity: NativeAddress
}
