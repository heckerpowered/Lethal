/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.image

import kotlin.math.ceil
import kotlin.math.log2

data class ImageSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0)
    }

    fun half() = ImageSize(maxOf(1, width / 2), maxOf(1, height / 2))
}

/**
 * Counts downsampling pyramid levels, including the source-size level and at least one level.
 * A power-of-two longest side greater than one ends at two texels rather than one.
 */
fun ImageSize.downsamplePyramidLevelCount(): Int =
    maxOf(1, ceil(log2(maxOf(width, height).toDouble())).toInt())
