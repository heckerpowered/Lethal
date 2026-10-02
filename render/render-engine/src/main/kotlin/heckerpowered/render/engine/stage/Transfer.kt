/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.TextureDataLayout
import heckerpowered.render.engine.geometry.UploadData
import heckerpowered.render.engine.prepare.BufferUpload
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * A deferred upload, copy, resolve, or discard recorded between render passes.
 *
 * Transfers let a stage declare resource preparation and image movement in the same ordered list
 * as drawing. They delegate access, format, sample-count, and region validation to the matching
 * RHI operation rather than deriving those requirements from an effect or geometry type.
 *
 * Upload variants copy their host bytes at construction. Resource selections remain borrowed and
 * their contents are read or written at the transfer's position in the command stream, not when
 * this value is created.
 */
sealed interface Transfer {
    fun encode(encoder: CommandEncoder)

    /**
     * Copies a host byte snapshot into exactly the selected destination buffer range.
     *
     * The byte count must equal the view length. Staging requires nonempty data; transfer usage and
     * resource validity are checked when the upload is recorded.
     */
    class UploadBuffer(destination: GpuBufferView, bytes: ByteArray) : Transfer {
        private val upload = BufferUpload(destination, UploadData(bytes))
        override fun encode(encoder: CommandEncoder) = upload.encode(encoder)
    }

    /**
     * Uploads a host byte snapshot using the supplied image region and texel layout.
     *
     * The byte count must exactly match the layout's required footprint, including row and image
     * padding. Construction validates that footprint; recording checks the destination's access.
     */
    class UploadImage(
        val destination: ImageRegion,
        val layout: TextureDataLayout,
        bytes: ByteArray,
    ) : Transfer {
        private val data = UploadData(bytes)

        init {
            require(layout.footprintFor(destination).requiredSizeBytes == data.sizeBytes.toLong())
        }

        override fun encode(encoder: CommandEncoder) {
            data.consumeNative(encoder.memoryStack) { address -> encoder.writeTexture(destination, address, layout) }
        }
    }

    class CopyImage(val source: ImageRegion, val destination: ImageRegion) : Transfer {
        override fun encode(encoder: CommandEncoder) = encoder.copyTexture(source, destination)
    }

    class Resolve(val source: ImageRegion, val destination: ImageRegion) : Transfer {
        override fun encode(encoder: CommandEncoder) = encoder.resolve(source, destination)
    }

    class BufferToImage(
        val source: GpuBufferView,
        val destination: ImageRegion,
        val layout: TextureDataLayout = TextureDataLayout.TightlyPacked,
    ) : Transfer {
        override fun encode(encoder: CommandEncoder) = encoder.copyBufferToTexture(source, destination, layout)
    }

    class ImageToBuffer(
        val source: ImageRegion,
        val destination: GpuBufferView,
        val layout: TextureDataLayout = TextureDataLayout.TightlyPacked,
    ) : Transfer {
        override fun encode(encoder: CommandEncoder) = encoder.copyTextureToBuffer(source, destination, layout)
    }

    class Discard(val region: ImageRegion) : Transfer {
        override fun encode(encoder: CommandEncoder) = encoder.discardContents(region)
    }
}
