/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.opengl.function.*
import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.BufferUsage.*
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.terminateOnFailure
import heckerpowered.render.resource.buffer.BufferUsage as ResourceBufferUsage

/** Owns one buffer allocation. Views borrow its byte ranges and never acquire a separate name. */
internal class OpenGLBuffer private constructor(val device: OpenGLGraphicsDevice, private val description: BufferDescription, private var bufferName: BufferName) : GpuBuffer {
    val name: BufferName
        get() {
            checkOpen()
            return bufferName
        }

    override val sizeBytes get() = description.sizeBytes
    override val usage get() = description.usage

    fun checkOpen() {
        device.checkAccess()
        check(bufferName != BufferName.None) { "Buffer '${description.label}' is closed" }
    }

    override fun close() = terminateOnFailure {
        if (bufferName == BufferName.None) return@terminateOnFailure
        context(device.functions) {
            device.checkAccess()
            checkError("before buffer destruction")
            deleteBuffer(bufferName)
            checkError("buffer destruction")
            bufferName = BufferName.None
        }
    }

    companion object {
        context(device: OpenGLGraphicsDevice)
        fun create(description: BufferDescription): OpenGLBuffer = context(device.functions) {
            device.checkAccess()
            validateDescription(description)
            checkError("before buffer creation")
            val name = allocateBufferName(description)

            // Retain cleanup responsibility until the wrapper exists. Finally covers Error exits
            // as well as ordinary exceptions without intercepting either at this boundary.
            var transferred = false
            try {
                withArrayBufferBinding(name) {
                    allocateStorage(description)
                }
                val buffer = OpenGLBuffer(device, description, name)
                transferred = true
                buffer
            } finally {
                if (!transferred) releaseBuffer(name)
            }
        }

        context(functions: OpenGLFunctions)
        private fun validateDescription(description: BufferDescription) {
            val fitsNativeSize = description.sizeBytes <= functions.maximumBufferSizeBytes
            if (!fitsNativeSize) throw UnsupportedOperationException("Buffer capacity exceeds this process's GLsizeiptr range: ${description.sizeBytes}")
            for (usage in description.usage) validateUsage(usage)
        }

        context(functions: OpenGLFunctions)
        private fun validateUsage(usage: ResourceBufferUsage) {
            when (usage) {
                Vertex, Index, TransferSource, TransferDestination -> Unit
                Uniform -> {
                    val hasUniformBuffers = functions.uniformBuffers != null
                    if (!hasUniformBuffers) throw UnsupportedOperationException("Uniform buffer storage requires native uniform-buffer support; uniform emulation is not implemented")
                }

                Storage -> TODO("Implement OpenGL storage-buffer access")
                UniformTexel -> TODO("Implement OpenGL uniform-texel-buffer views")
                StorageTexel -> TODO("Implement OpenGL storage-texel-buffer views")
                Indirect -> TODO("Implement OpenGL indirect-buffer commands")
                QueryResolve -> TODO()
            }
        }

        context(_: OpenGLFunctions)
        private fun allocateBufferName(description: BufferDescription): BufferName {
            val name = createBuffer()
            if (name != BufferName.None) return name
            checkError("buffer name allocation")
            error("OpenGL returned no name for buffer '${description.label}'")
        }

        context(_: OpenGLFunctions)
        private fun allocateStorage(description: BufferDescription) {
            // RHI usage grants access; GL usage is only a placement hint. Neither initializes bytes.
            val allocationHint = if (TransferDestination in description.usage) BufferUsage.DynamicDraw else BufferUsage.StaticDraw
            bufferData(BufferTarget.Array, description.sizeBytes, allocationHint)
            checkError("buffer '${description.label}' allocation")
        }
    }
}

context(device: OpenGLGraphicsDevice)
internal fun uploadBuffer(destination: GpuBufferView, sourceAddress: NativeAddress) = context(device.functions) {
    val buffer = device.requireBuffer(destination.buffer)
    destination.validateUpload(sourceAddress)
    if (destination.sizeBytes == 0L) return@context

    checkError("before buffer upload")
    withArrayBufferBinding(buffer.name) {
        bufferSubData(BufferTarget.Array, destination.offsetBytes, destination.sizeBytes, sourceAddress)
        checkError("buffer upload")
    }
}

/**
 * Borrows a name for storage operations and restores the host binding on every exit.
 * ARRAY_BUFFER works on the baseline and does not change a vertex array's element-buffer binding.
 * A buffer's eventual RHI role does not require allocating or updating it through that role's GL target.
 */
context(device: OpenGLGraphicsDevice, _: OpenGLFunctions)
private inline fun <R> withArrayBufferBinding(buffer: BufferName, operation: () -> R): R {
    val previousBuffer = getBoundBuffer(BufferTarget.Array)
    checkError("array-buffer binding snapshot")
    try {
        bindBuffer(BufferTarget.Array, buffer)
        checkError("array-buffer binding")
        return operation()
    } finally {
        terminateOnFailure {
            device.checkAccess()
            bindBuffer(BufferTarget.Array, previousBuffer)
            checkError("array-buffer binding restoration")
        }
    }
}

context(device: OpenGLGraphicsDevice, _: OpenGLFunctions)
private fun releaseBuffer(buffer: BufferName) = terminateOnFailure {
    device.checkAccess()
    deleteBuffer(buffer)
    checkError("failed buffer creation cleanup")
}
