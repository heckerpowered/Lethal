/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MathTest {
    @Test
    fun squareMultipliesTheValueByItself() {
        assertEquals(expected = 9.0, actual = (-3.0).square())
        assertEquals(expected = 0.0, actual = 0.0.square())
        assertEquals(expected = 6.25, actual = 2.5.square())
    }

    @Test
    fun nearlyZeroIncludesBothToleranceBoundaries() {
        assertTrue(0.0.isNearlyZero())
        assertTrue(1.0E-8.isNearlyZero())
        assertTrue((-1.0E-8).isNearlyZero())
        assertFalse(1.1E-8.isNearlyZero())
        assertFalse((-1.1E-8).isNearlyZero())
        assertTrue(0.25.isNearlyZero(epsilon = 0.25))
        assertFalse(0.2501.isNearlyZero(epsilon = 0.25))
    }
}
