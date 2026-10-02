/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry.index

import heckerpowered.render.engine.geometry.UploadData
import heckerpowered.render.pipeline.primitive.IndexFormat

/**
 * Supplies index entries that select vertex elements for indexed geometry.
 *
 * [format] describes the stored width and restart marker. Draw ranges count index entries,
 * including restart markers, rather than bytes or distinct vertices. Neither source form
 * reads the index values on the CPU to validate their eventual vertex selections.
 */
sealed interface IndexSource {
    val format: IndexFormat

    /** Uses an existing validated GPU selection without snapshotting its contents. */
    data class Resident(val selection: IndexSelection) : IndexSource {
        override val format get() = selection.format
    }

    /**
     * Snapshots host bytes for later upload as index storage. Bytes must already be encoded in
     * [format] and the byte order expected by the device; preparation does not convert them.
     */
    class Upload(override val format: IndexFormat, bytes: ByteArray) : IndexSource {
        val bytes = UploadData(bytes)
    }
}
