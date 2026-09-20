/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

/**
 * Chooses the initial contents and preservation of one attachment use in a logical render pass.
 *
 * A scene pass might clear color and preserve its result; an overlay then loads that color
 * before adding to it. These choices describe the use, not permanent settings of the image.
 *
 * The containing pass position determines the aspect, and its render area and layer count
 * determine the affected range. Depth and stencil can therefore have different operations even
 * when supplied by the same combined attachment.
 *
 * `T` is the value accepted by [AttachmentLoadOperation.Clear]. Load and Discard need no value.
 */
data class AttachmentOperations<out T>(
    val load: AttachmentLoadOperation<T>,
    val store: AttachmentStoreOperation,
) {
    companion object {
        /**
         * Continues from existing contents and preserves the result for later operations.
         *
         * This is convenient for overlays and incremental drawing. It does not initialize a newly
         * created or discarded image; use an explicit Clear when known initial values are needed.
         */
        val Default: AttachmentOperations<Nothing> = AttachmentOperations(
            load = AttachmentLoadOperation.Load,
            store = AttachmentStoreOperation.Store,
        )
    }
}
