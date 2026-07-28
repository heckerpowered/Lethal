/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.ints
import heckerpowered.render.memory.alloc
import org.lwjgl.opengl.GL11

/**
 * Supplies the active OpenGL viewport without retaining temporary native memory.
 */
internal fun <Result> queryOpenGLViewport(binding: OpenGLBinding, memoryStack: MemoryStack, result: (x: Int, y: Int, width: Int, height: Int) -> Result): Result {
    return memoryStack.alloc(ints(4)) { viewport ->
        binding.getInteger(GL11.GL_VIEWPORT, viewport)
        result(loadInt(viewport), loadInt(viewport + Int.SIZE_BYTES), loadInt(viewport + Int.SIZE_BYTES * 2), loadInt(viewport + Int.SIZE_BYTES * 3))
    }
}
