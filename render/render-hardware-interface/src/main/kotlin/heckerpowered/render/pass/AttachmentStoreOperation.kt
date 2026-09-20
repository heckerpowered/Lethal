/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

/**
 * Determines whether an attachment use's contents remain needed after a logical render pass.
 *
 * A scene image consumed later must be preserved. Depth or stencil data used only to produce
 * that image may instead be discarded. Each pass position chooses for its own aspect, within
 * the pass's render area and participating layers; it does not discard the other aspect of a
 * combined depth-stencil image.
 *
 * These are content guarantees, not requests for particular native store instructions. Logical
 * passes and image operations may be grouped into one native rendering scope, provided their
 * reads, writes, and content-validity rules are preserved.
 */
enum class AttachmentStoreOperation {
    /**
     * Keeps the result available to later operations until it is replaced, discarded, or leaves
     * the source's permitted content scope.
     *
     * Use this when later drawing, sampling, or resolve needs the image. For example, a logical
     * pass can Store its multisampled color for a following resolve, then explicitly discard the
     * source after resolve. A backend can keep the samples locally until that consumer finishes;
     * Store does not require an intermediate write to backing memory.
     *
     * This does not make previously undefined, unwritten values valid, establish synchronization,
     * or make memoryless contents survive a native scope in which they cannot be retained.
     */
    Store,

    /**
     * Gives up this use's contents when the logical pass ends.
     *
     * Choose this when the result is no longer needed, for example temporary depth data after
     * visibility testing. Rendering still takes place; only the requirement to preserve the
     * resulting values is removed.
     *
     * The affected values are undefined afterward and must be established again before a read
     * depends on them. In particular, a later logical resolve cannot read a source discarded
     * here, even if a backend could otherwise fuse the operations.
     *
     * This neither clears nor destroys the image. Values outside the selected pass region and
     * aspect are not discarded by this choice, although backend synchronization can cover a
     * larger range than the logical content effect.
     */
    Discard,
}
