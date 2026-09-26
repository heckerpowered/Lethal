/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.color.Color
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.opengl.function.*
import heckerpowered.render.terminateOnFailure

/**
 * Makes [attachment] the color target during [operation], then restores the host's framebuffer bindings.
 * Only the temporary framebuffer connection is owned by this scope; the image storage remains borrowed.
 */
context(device: OpenGLGraphicsDevice)
internal inline fun <R> withColorFramebuffer(attachment: OpenGLRenderAttachment, operation: () -> R): R {
    val framebufferFunctions = device.functions.framebuffers ?: throw UnsupportedOperationException("Framebuffer objects are unavailable")
    return context(device.functions, framebufferFunctions) {
        withTemporaryFramebufferBinding {
            attachColorImage(attachment)
            operation()
        }
    }
}

context(device: OpenGLGraphicsDevice, _: OpenGLStateFunctions, _: OpenGLFramebufferFunctions)
private inline fun <R> withTemporaryFramebufferBinding(operation: () -> R): R {
    val previousDrawFramebuffer = getBoundDrawFramebuffer()
    val previousReadFramebuffer = getBoundReadFramebuffer()
    checkError("framebuffer binding snapshot")

    val temporaryFramebuffer = createFramebuffer()
    check(temporaryFramebuffer != FramebufferName.Default) { "OpenGL returned framebuffer name zero" }
    try {
        bindDrawFramebuffer(temporaryFramebuffer)
        bindReadFramebuffer(temporaryFramebuffer)
        return operation()
    } finally {
        terminateOnFailure {
            device.checkAccess()
            // Restore before deleting the temporary object; EXT aliases these two bindings.
            bindDrawFramebuffer(previousDrawFramebuffer)
            bindReadFramebuffer(previousReadFramebuffer)
            deleteFramebuffer(temporaryFramebuffer)
            checkError("framebuffer restoration and release")
        }
    }
}

context(_: OpenGLStateFunctions, _: OpenGLFramebufferFunctions)
private fun attachColorImage(attachment: OpenGLRenderAttachment) {
    framebufferTexture2D(GL_COLOR_ATTACHMENT0, attachment.texture.name, attachment.view.baseMipLevel)
    val status = checkFramebufferStatus()
    checkError("framebuffer setup")

    val isFramebufferComplete = status == GL_FRAMEBUFFER_COMPLETE
    if (!isFramebufferComplete) throw UnsupportedOperationException("Framebuffer is incomplete: 0x${status.toUInt().toString(16)}")
}

/**
 * Clears [area] in the color attachment already connected by [withColorFramebuffer].
 * The surrounding scope restores clear configuration, not the image contents changed here.
 */
context(device: OpenGLGraphicsDevice)
internal fun clearBoundColorAttachment(attachment: OpenGLRenderAttachment, area: RenderArea, value: Color) = context(device.functions) {
    withPreservedColorClearState {
        configureColorClear(attachment.height, area, value)
        clear(GL_COLOR_BUFFER_BIT)
        checkError("color attachment clear")
    }
}

/** Sets an exact-area, all-channel clear rather than inheriting the host's drawing configuration. */
context(_: OpenGLStateFunctions)
private fun configureColorClear(attachmentHeight: Int, area: RenderArea, value: Color) {
    // RHI areas start at the top-left; OpenGL scissor coordinates start at the bottom-left.
    val bottomLeftY = attachmentHeight - area.y - area.height
    scissor(0, area.x, bottomLeftY, area.width, area.height)
    setScissorEnabled(0, true)
    colorMask(0, red = true, green = true, blue = true, alpha = true)
    disable(GL_DITHER)
    if (supportsRasterizerDiscard) disable(GL_RASTERIZER_DISCARD)
    clearColor(value.red, value.green, value.blue, value.alpha)
    checkError("color clear configuration")
}

/**
 * Preserves the parameters and enable flags changed by [configureColorClear], without choosing new values.
 * The generated allocation holds all query results until restoration finishes, including exceptional exits.
 */
context(device: OpenGLGraphicsDevice, _: OpenGLStateFunctions)
private inline fun <R> withPreservedColorClearState(operation: () -> R): R = device.memoryStack.withOpenGLClearState {
    val previous = this
    getScissorBox(0, previous.scissor.asIntBuffer())
    getIntegers(GL_COLOR_WRITEMASK, previous.colorWriteMask.asIntBuffer())
    getFloats(GL_COLOR_CLEAR_VALUE, previous.clearColor.asFloatBuffer())
    val scissorWasEnabled = isScissorEnabled(0)
    val ditherWasEnabled = isEnabled(GL_DITHER)
    val rasterizerDiscardWasEnabled = supportsRasterizerDiscard && isEnabled(GL_RASTERIZER_DISCARD)
    checkError("clear-state snapshot")

    try {
        operation()
    } finally {
        terminateOnFailure {
            device.checkAccess()
            clearColor(previous.clearColor.red, previous.clearColor.green, previous.clearColor.blue, previous.clearColor.alpha)
            colorMask(0, red = previous.colorWriteMask.red != 0, green = previous.colorWriteMask.green != 0, blue = previous.colorWriteMask.blue != 0, alpha = previous.colorWriteMask.alpha != 0)
            scissor(0, previous.scissor.x, previous.scissor.y, previous.scissor.width, previous.scissor.height)
            setScissorEnabled(0, scissorWasEnabled)
            setCapabilityEnabled(GL_DITHER, ditherWasEnabled)
            if (supportsRasterizerDiscard) setCapabilityEnabled(GL_RASTERIZER_DISCARD, rasterizerDiscardWasEnabled)
            checkError("clear-state restoration")
        }
    }
}

context(_: OpenGLStateFunctions)
private fun setCapabilityEnabled(capability: Int, enabled: Boolean) {
    if (enabled) enable(capability) else disable(capability)
}
