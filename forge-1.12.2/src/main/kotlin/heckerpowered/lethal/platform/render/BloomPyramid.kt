/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

internal data class FramebufferDimensions(val width: Int, val height: Int)

internal fun bloomPyramidDimensions(width: Int, height: Int): List<FramebufferDimensions> {
    require(width > 0 && height > 0) { "Framebuffer dimensions must be positive" }

    val maximumDimension = maxOf(width, height)
    val levelCount = (Int.SIZE_BITS - Integer.numberOfLeadingZeros(maximumDimension - 1)).coerceAtLeast(1)
    val dimensions = mutableListOf<FramebufferDimensions>()
    var levelWidth = width
    var levelHeight = height

    while (dimensions.size < levelCount) {
        dimensions += FramebufferDimensions(levelWidth, levelHeight)
        levelWidth = (levelWidth / 2).coerceAtLeast(1)
        levelHeight = (levelHeight / 2).coerceAtLeast(1)
    }

    return dimensions
}
