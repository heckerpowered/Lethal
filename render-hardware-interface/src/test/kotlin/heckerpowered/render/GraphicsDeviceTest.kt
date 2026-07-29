/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import kotlin.test.Test
import kotlin.test.assertFalse

class GraphicsDeviceTest {
    @Test
    fun graphicsDeviceDoesNotOwnTheHostDeviceLifetime() {
        assertFalse(actual = AutoCloseable::class.java.isAssignableFrom(GraphicsDevice::class.java))
    }
}
