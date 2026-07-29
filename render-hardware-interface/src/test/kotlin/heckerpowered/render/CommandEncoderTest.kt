/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import kotlin.test.Test
import kotlin.test.assertFalse

class CommandEncoderTest {
    @Test
    fun commandEncoderDoesNotOwnItsRecordingLifetime() {
        assertFalse(actual = AutoCloseable::class.java.isAssignableFrom(CommandEncoder::class.java))
    }
}
