/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.texture

/**
 * Reports a backend failure to establish texture storage, such as an allocation failure.
 *
 * No usable texture is returned from the failed creation. The message should identify the
 * requested texture and include available backend diagnostics. Unsupported requirements are
 * reported separately with [UnsupportedOperationException].
 */
class TextureCreationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
