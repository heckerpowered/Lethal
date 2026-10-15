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
     * Snapshots immutable vertex data shared across draws and stages.
     *
     * Each engine creates and owns its own GPU storage on first use, retaining it until engine
     * closure. Reusing this source identity reuses that storage; equal bytes in distinct sources
     * remain independent. The source has no update operation. Construction samples the caller's
     * array without synchronizing concurrent writes.
     */
    class Static(bytes: ByteArray) : VertexStreamSource {
        val bytes = UploadData(bytes)
    }

    /**
     * Copies host bytes immediately; preparation creates vertex storage and records an upload
     * when the shader input contract consumes this stream.
     */
    class Upload(bytes: ByteArray) : VertexStreamSource {
        val bytes = UploadData(bytes)
    }
}
