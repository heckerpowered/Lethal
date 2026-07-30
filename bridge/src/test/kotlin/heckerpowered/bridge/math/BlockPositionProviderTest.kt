/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import heckerpowered.bridge.FreestandingRepresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class BlockPositionProviderTest {
    @Test
    fun freestandingProviderCreatesAndPacksBlockPositions() {
        val position = FreestandingBlockPositionProvider.position(12, -34, 56)

        assertIs<FreestandingRepresentation>(position)
        assertBlockPosition(12, -34, 56, position)

        val packed = FreestandingBlockPositionProvider.asLong(position)
        assertBlockPosition(12, -34, 56, FreestandingBlockPositionProvider.fromPackedLong(packed))
    }

    @Test
    fun defaultProviderMethodsUseFreestandingPositions() {
        val provider = object : BlockPositionProvider {}

        val position = provider.position(12, -34, 56)
        val containing = provider.containing(1.9, -2.1, 3.0)
        val packed = provider.asLong(position)

        assertIs<FreestandingRepresentation>(position)
        assertBlockPosition(1, -3, 3, containing)
        assertBlockPosition(12, -34, 56, provider.fromPackedLong(packed))
    }

    @Test
    fun blockPositionFacadeUsesItsConfiguredProvider() {
        val position = BlockPositions.of(12, -34, 56)
        val packed = position.asLong()

        assertBlockPosition(12, -34, 56, position)
        assertBlockPosition(1, -3, 3, BlockPositions.containing(1.9, -2.1, 3.0))
        assertEquals(expected = packed, actual = BlockPositions.asLong(position))
        assertBlockPosition(12, -34, 56, BlockPositions.fromPackedLong(packed))
        assertSame(expected = FreestandingBlockPositionProvider, actual = BlockPositionProvider.Freestanding)
    }

    @Test
    fun freestandingProviderSupportsExplicitPackingFormats() {
        val position = FreestandingBlockPositionProvider.position(12, -34, 56)

        for (format in listOf(BlockPositionPackingFormats.XYZ, BlockPositionPackingFormats.XZY)) {
            val packed = FreestandingBlockPositionProvider.asLong(position, format)

            assertBlockPosition(12, -34, 56, FreestandingBlockPositionProvider.fromPackedLong(packed, format))
        }
    }

    @Test
    fun automaticProviderFallsBackWhenNoHostProviderIsInstalled() {
        assertNull(BlockPositionProvider.Hosting)
        assertSame(expected = BlockPositionProvider.Freestanding, actual = BlockPositionProvider.Auto)
        assertSame(expected = BlockPositionProvider.Auto, actual = BlockPositions.Provider)
    }
}
