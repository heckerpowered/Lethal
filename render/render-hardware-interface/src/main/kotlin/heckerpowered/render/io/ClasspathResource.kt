/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.io

import java.io.InputStream

/**
 * Opens a packaged resource through this helper's class loader and module.
 *
 * Use a leading `/` to resolve from the classpath root. Relative paths instead resolve
 * within `heckerpowered.render.io`. The caller owns the stream and must close it.
 *
 * @throws IllegalArgumentException if the resource is not found.
 */
fun resource(path: String): InputStream {
    class Empty
    return Empty::class.java.getResourceAsStream(path) ?: throw IllegalArgumentException("Resource $path not found")
}
