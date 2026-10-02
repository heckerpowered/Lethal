/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry.vertex

import heckerpowered.render.engine.geometry.UploadData
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * Supplies the bytes for one geometry stream from existing GPU storage or a host snapshot.
 *
 * The corresponding stream layout determines how those bytes are interpreted. Source selection
 * does not assign shader input locations or infer an element count.
 */
sealed interface VertexStreamSource {
    /**
     * Uses the selected GPU range directly, with no copy of its contents. The caller must keep
     * the buffer valid and coordinate any writes through completion of the consuming draw.
     */
    data class Resident(val view: GpuBufferView) : VertexStreamSource

    /**
     * Copies host bytes immediately; preparation creates vertex storage and records an upload
     * when the shader input contract consumes this stream.
     */
    class Upload(bytes: ByteArray) : VertexStreamSource {
        val bytes = UploadData(bytes)
    }
}
