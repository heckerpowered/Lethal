/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.image

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageSizeTest {
    @Test
    fun downsamplePyramidLevelCountUsesTheLongestSideAndRetainsNumericBoundaries() {
        for ((longestSide, count) in listOf(
            1 to 1, 2 to 1, 3 to 2, 4 to 2, 5 to 3, 6 to 3, 7 to 3, 8 to 3,
            9 to 4, 15 to 4, 16 to 4, 17 to 5,
            1_073_741_823 to 30, 1_073_741_824 to 30, 1_073_741_825 to 31,
            Int.MAX_VALUE to 31,
        )) {
            assertEquals(count, ImageSize(longestSide, 1).downsamplePyramidLevelCount(), "width=$longestSide")
            assertEquals(count, ImageSize(1, longestSide).downsamplePyramidLevelCount(), "height=$longestSide")
        }
    }

    @Test
    fun downsamplePyramidRetainsSourceSizeAndExistingHalvingEndpoints() {
        for ((source, sizes) in listOf(
            ImageSize(1, 1) to listOf(ImageSize(1, 1)),
            ImageSize(2, 2) to listOf(ImageSize(2, 2)),
            ImageSize(8, 8) to listOf(ImageSize(8, 8), ImageSize(4, 4), ImageSize(2, 2)),
            ImageSize(3, 5) to listOf(ImageSize(3, 5), ImageSize(1, 2), ImageSize(1, 1)),
            ImageSize(1, 7) to listOf(ImageSize(1, 7), ImageSize(1, 3), ImageSize(1, 1)),
            ImageSize(16, 9) to listOf(ImageSize(16, 9), ImageSize(8, 4), ImageSize(4, 2), ImageSize(2, 1)),
        )) {
            assertEquals(sizes, generateSequence(source) { it.half() }.take(source.downsamplePyramidLevelCount()).toList())
        }
    }
}
