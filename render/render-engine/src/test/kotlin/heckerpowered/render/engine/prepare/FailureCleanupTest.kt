/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.prepare

import heckerpowered.render.withFailureCleanup
import kotlin.test.*

class FailureCleanupTest {
    @Test
    fun successfulResultsIncludingNullDoNotRunCleanup() {
        val result = Any()
        assertSame(result, withFailureCleanup(operation = { result }) { error("Unexpected cleanup") })
        assertNull(withFailureCleanup(operation = { null }) { error("Unexpected cleanup") })
    }

    @Test
    fun exceptionsAndErrorsRunCleanupOnceAndPropagateUnchanged() {
        listOf(IllegalStateException("operation failed"), AssertionError("operation failed")).forEach { failure ->
            val events = mutableListOf<String>()
            val actual = assertFails {
                withFailureCleanup(
                    operation = {
                        events += "operation"
                        throw failure
                    },
                ) { events += "cleanup" }
            }
            assertSame(failure, actual)
            assertEquals(listOf("operation", "cleanup"), events)
        }
    }
}
