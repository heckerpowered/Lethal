/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.image

data class ImageSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0)
    }

    fun half() = ImageSize(maxOf(1, width / 2), maxOf(1, height / 2))
}
