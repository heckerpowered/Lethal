/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BlockPositionPackingFormatTest {
    @Test
    fun xyzAndXzyRoundTripTheirFullSignedRanges() {
        val coordinates = listOf(
            Triple(0, 0, 0),
            Triple(1, 1, 1),
            Triple(-1, -1, -1),
            Triple(MINIMUM_HORIZONTAL, MINIMUM_VERTICAL, MINIMUM_HORIZONTAL),
            Triple(MAXIMUM_HORIZONTAL, MAXIMUM_VERTICAL, MAXIMUM_HORIZONTAL),
            Triple(MINIMUM_HORIZONTAL, MAXIMUM_VERTICAL, MAXIMUM_HORIZONTAL),
            Triple(MAXIMUM_HORIZONTAL, MINIMUM_VERTICAL, MINIMUM_HORIZONTAL),
        )

        for (format in listOf(BlockPositionPackingFormats.XYZ, BlockPositionPackingFormats.XZY)) {
            for ((x, y, z) in coordinates) {
                val packed = format.pack(x, y, z)

                assertEquals(expected = x, actual = format.unpackX(packed), message = "$format x")
                assertEquals(expected = y, actual = format.unpackY(packed), message = "$format y")
                assertEquals(expected = z, actual = format.unpackZ(packed), message = "$format z")
            }
        }
    }

    @Test
    fun layoutsUseDifferentBitOrdersWithoutChangingCoordinates() {
        val xyz = BlockPositionPackingFormats.XYZ.pack(12, -34, 56)
        val xzy = BlockPositionPackingFormats.XZY.pack(12, -34, 56)

        assertNotEquals(illegal = xyz, actual = xzy)
        assertEquals(expected = 12, actual = BlockPositionPackingFormats.XYZ.unpackX(xyz))
        assertEquals(expected = -34, actual = BlockPositionPackingFormats.XYZ.unpackY(xyz))
        assertEquals(expected = 56, actual = BlockPositionPackingFormats.XYZ.unpackZ(xyz))
        assertEquals(expected = 12, actual = BlockPositionPackingFormats.XZY.unpackX(xzy))
        assertEquals(expected = -34, actual = BlockPositionPackingFormats.XZY.unpackY(xzy))
        assertEquals(expected = 56, actual = BlockPositionPackingFormats.XZY.unpackZ(xzy))
    }

    @Test
    fun packingFacadeAndPositionExtensionsHonorExplicitFormats() {
        val position = BlockPositions.of(12, -34, 56)

        for (format in listOf(BlockPositionPackingFormats.XYZ, BlockPositionPackingFormats.XZY)) {
            val packed = BlockPositionPacking.pack(position.x, position.y, position.z, format)

            assertEquals(expected = packed, actual = position.pack(format))
            assertEquals(expected = packed, actual = position.asLong(format))
            assertEquals(expected = position.x, actual = BlockPositionPacking.unpackX(packed, format))
            assertEquals(expected = position.y, actual = BlockPositionPacking.unpackY(packed, format))
            assertEquals(expected = position.z, actual = BlockPositionPacking.unpackZ(packed, format))
            assertBlockPosition(position.x, position.y, position.z, BlockPositions.fromPackedLong(packed, format))
        }
    }

    @Test
    fun autoFormatDelegatesToTheConfiguredBlockPositionProvider() {
        val position = BlockPositions.of(12, -34, 56)
        val packed = BlockPositionPackingFormats.Auto.pack(position.x, position.y, position.z)

        assertEquals(expected = BlockPositions.Provider.asLong(position), actual = packed)
        assertEquals(expected = position.x, actual = BlockPositionPackingFormats.Auto.unpackX(packed))
        assertEquals(expected = position.y, actual = BlockPositionPackingFormats.Auto.unpackY(packed))
        assertEquals(expected = position.z, actual = BlockPositionPackingFormats.Auto.unpackZ(packed))
    }

    @Test
    fun defaultPackingFacadeUsesTheCurrentDefaultFormat() {
        val expected = BlockPositionPackingFormats.Default.pack(12, -34, 56)
        val packed = BlockPositionPacking.pack(12, -34, 56)

        assertEquals(expected = expected, actual = packed)
        assertEquals(expected = 12, actual = BlockPositionPacking.unpackX(packed))
        assertEquals(expected = -34, actual = BlockPositionPacking.unpackY(packed))
        assertEquals(expected = 56, actual = BlockPositionPacking.unpackZ(packed))
    }

    @Test
    fun lowLevelPackingHelpersMaskAndSignExtendTheirFields() {
        val packed = BlockPositionPacking.packWithOffsets(12, -34, 56, 38, 26, 0)

        assertEquals(expected = BlockPositionPackingFormats.XYZ.pack(12, -34, 56), actual = packed)
        assertEquals(expected = 12, actual = BlockPositionPacking.unpackSigned(packed, 38, BlockPositionPacking.HORIZONTAL_BITS))
        assertEquals(expected = -34, actual = BlockPositionPacking.unpackSigned(packed, 26, BlockPositionPacking.VERTICAL_BITS))
        assertEquals(expected = 56, actual = BlockPositionPacking.unpackSigned(packed, 0, BlockPositionPacking.HORIZONTAL_BITS))
    }

    @Test
    fun packingConstantsDescribeTwentySixHorizontalAndTwelveVerticalBits() {
        assertEquals(expected = 26, actual = BlockPositionPacking.HORIZONTAL_BITS)
        assertEquals(expected = 12, actual = BlockPositionPacking.VERTICAL_BITS)
        assertEquals(expected = (1L shl 26) - 1L, actual = BlockPositionPacking.HORIZONTAL_MASK)
        assertEquals(expected = (1L shl 12) - 1L, actual = BlockPositionPacking.VERTICAL_MASK)
    }

    private companion object {
        private const val MINIMUM_HORIZONTAL = -(1 shl 25)
        private const val MAXIMUM_HORIZONTAL = (1 shl 25) - 1
        private const val MINIMUM_VERTICAL = -(1 shl 11)
        private const val MAXIMUM_VERTICAL = (1 shl 11) - 1
    }
}
