/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.material.parameter

import kotlin.test.*

class ParameterValuesTest {
    @Test
    fun emptyConstructionSupportsDefaultMapAndVarargCalls() {
        assertTrue(ParameterValues().values.isEmpty())
        assertTrue(ParameterValues(emptyMap()).values.isEmpty())
        assertTrue(ParameterValues(*emptyArray<Pair<ParameterName, ParameterValue>>()).values.isEmpty())
    }

    @Test
    fun repeatedNamesKeepLastValueAndFirstInsertionOrder() {
        val firstName = ParameterName("first")
        val secondName = ParameterName("second")
        val initialValue = NumericParameterValue.floats(1F)
        val replacementValue = NumericParameterValue.floats(2F)
        val parameters = ParameterValues(firstName to initialValue, secondName to initialValue, firstName to replacementValue)
        assertEquals(listOf(firstName, secondName), parameters.values.keys.toList())
        assertSame(replacementValue, parameters.require(firstName))
        assertSame(initialValue, parameters.require(secondName))
    }

    @Test
    fun mapAndVarargInputsRemainIndependentSnapshotsThatRejectMutation() {
        val name = ParameterName("color")
        val value = NumericParameterValue.floats(1F)
        val replacementValue = NumericParameterValue.floats(2F)
        val sourceMap = linkedMapOf<ParameterName, ParameterValue>(name to value)
        val sourceEntries = arrayOf<Pair<ParameterName, ParameterValue>>(name to value)
        val fromMap = ParameterValues(sourceMap)
        val fromEntries = ParameterValues(*sourceEntries)
        sourceMap.clear()
        sourceEntries[0] = name to replacementValue
        for (parameters in listOf(fromMap, fromEntries)) {
            assertSame(value, parameters.require(name))
            val exposed = parameters.values as MutableMap<ParameterName, ParameterValue>
            assertFailsWith<UnsupportedOperationException> { exposed[name] = replacementValue }
            assertFailsWith<UnsupportedOperationException> { exposed.entries.single().setValue(replacementValue) }
            assertSame(value, parameters.require(name))
        }
    }
}
