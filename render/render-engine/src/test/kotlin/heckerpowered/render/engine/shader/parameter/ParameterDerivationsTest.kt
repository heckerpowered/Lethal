/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.parameter

import heckerpowered.math.Vector
import heckerpowered.math.Matrices
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.shader.binding.PushConstantField
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ParameterDerivationsTest {
    @Test
    fun packingPlacesRawBytesAtDeclaredOffsetsAndLeavesGapsZero() {
        val input = parameters("first" to byteArrayOf(1, 2), "second" to byteArrayOf(3))
        val packing = NumericPacking(ParameterName("packed"), 7, listOf(field("second", 5, 1), field("first", 1, 2)))
        val output = packing.derive(input)
        assertContentEquals(byteArrayOf(0, 1, 2, 0, 0, 3, 0), output.requireNumeric(ParameterName("packed")).bytes())
        assertSame(input.requireNumeric(ParameterName("first")), output.requireNumeric(ParameterName("first")))
        assertEquals(setOf(ParameterName("first"), ParameterName("second")), input.values.keys)
    }

    @Test
    fun emptyFieldListProducesAZeroFilledBlock() {
        val output = NumericPacking(ParameterName("packed"), 3, emptyList()).derive(ParameterValues())
        assertContentEquals(ByteArray(3), output.requireNumeric(ParameterName("packed")).bytes())
    }

    @Test
    fun invalidLayoutsAreRejectedAtConstruction() {
        for (sizeBytes in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> { NumericPacking(ParameterName("packed"), sizeBytes, emptyList()) }
        }
        for (invalidField in listOf(field("value", -1, 2), field("value", 0, 0), field("value", 0, -1), field("value", 3, 2), field("value", Int.MAX_VALUE, 2))) {
            assertFailsWith<IllegalArgumentException> { NumericPacking(ParameterName("packed"), 4, listOf(invalidField)) }
        }
    }

    @Test
    fun packingRejectsOverlapAndMismatchedInputSize() {
        assertFailsWith<IllegalArgumentException> {
            NumericPacking(ParameterName("packed"), 4, listOf(field("value", 0, 2), field("value", 1, 2)))
        }
        val wrongSize = NumericPacking(ParameterName("packed"), 4, listOf(field("value", 0, 1)))
        val failure = assertFailsWith<IllegalArgumentException> { wrongSize.derive(parameters("value" to byteArrayOf(1, 2))) }
        assertEquals("Packed field value requires 1 bytes, but its input contains 2", failure.message)
    }

    @Test
    fun fixedLayoutIsValidatedBeforeAnyInputLookup() {
        val failure = assertFailsWith<IllegalArgumentException> {
            NumericPacking(ParameterName("packed"), 4, listOf(field("missing", 0, 1), field("later", 4, 1)))
        }
        assertEquals("Packed field later at offset 4 with size 1 does not fit in packed's 4-byte block", failure.message)
    }

    @Test
    fun overlapIsRejectedWithoutRequiringFieldInputs() {
        val failure = assertFailsWith<IllegalArgumentException> {
            NumericPacking(ParameterName("packed"), 4, listOf(field("value", 0, 2), field("missing", 1, 2)))
        }
        assertEquals("Packed field missing overlaps another field in packed", failure.message)
    }

    @Test
    fun fieldBoundsAtIntCapacityDoNotWrapOrAllocateTheBlockAtConstruction() {
        val packing = NumericPacking(ParameterName("packed"), Int.MAX_VALUE, listOf(field("last", Int.MAX_VALUE - 1, 1)))
        assertEquals(Int.MAX_VALUE - 1, packing.fields.single().offsetBytes)
        assertFailsWith<IllegalArgumentException> {
            NumericPacking(ParameterName("packed"), Int.MAX_VALUE, listOf(field("overflow", Int.MAX_VALUE - 1, 2)))
        }
        assertFailsWith<IllegalArgumentException> {
            NumericPacking(ParameterName("packed"), 4, listOf(field("oversized", 0, Int.MAX_VALUE)))
        }
    }

    @Test
    fun validLayoutStillRejectsMissingInputDuringDerivation() {
        val packing = NumericPacking(ParameterName("packed"), 4, listOf(field("missing", 0, 1)))
        val failure = assertFailsWith<IllegalArgumentException> { packing.derive(ParameterValues()) }
        assertEquals("Missing shader parameter missing", failure.message)
    }

    @Test
    fun providerConflictRemainsAfterFieldValidation() {
        val input = parameters("packed" to byteArrayOf(0))
        val packing = NumericPacking(ParameterName("packed"), 4, listOf(field("missing", 0, 1)))
        val failure = assertFailsWith<IllegalArgumentException> { packing.derive(input) }
        assertEquals("Missing shader parameter missing", failure.message)
        val conflict = NumericPacking(ParameterName("packed"), 4, emptyList())
        val conflictFailure = assertFailsWith<IllegalArgumentException> { conflict.derive(input) }
        assertEquals("Parameter derivation overwrites a provider", conflictFailure.message)
        assertContentEquals(byteArrayOf(0), input.requireNumeric(ParameterName("packed")).bytes())
    }

    @Test
    fun laterPackingMayReadEarlierDerivedValues() {
        val first = NumericPacking(ParameterName("first"), 4, listOf(field("value", 1, 2)))
        val second = NumericPacking(ParameterName("second"), 6, listOf(field("first", 2, 4)))
        val output = ParameterDerivations(packing = listOf(first, second)).resolve(parameters("value" to byteArrayOf(7, 8)), Matrices.Identity)
        assertContentEquals(byteArrayOf(0, 7, 8, 0), output.requireNumeric(ParameterName("first")).bytes())
        assertContentEquals(byteArrayOf(0, 0, 0, 7, 8, 0), output.requireNumeric(ParameterName("second")).bytes())
    }

    @Test
    fun unrequestedNormalMatrixDoesNotInspectSingularTransform() {
        val input = ParameterValues()
        val singular = Matrices.fromScale(Vector(0.0, 1.0, 1.0))
        assertSame(input, ParameterDerivations().resolve(input, singular))
        assertFailsWith<IllegalArgumentException> { ParameterDerivations(true).resolve(input, singular) }
    }

    @Test
    fun requestedNormalMatrixKeepsColumnMajorMat4PaddingAndInverseScale() {
        val output = ParameterDerivations(true).resolve(ParameterValues(), Matrices.fromScale(Vector(2.0, 4.0, 8.0)))
        val bytes = output.requireNumeric(ParameterName("normalFromLocal")).bytes()
        assertEquals(64, bytes.size)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val values = FloatArray(16)
        buffer.get(values)
        assertContentEquals(floatArrayOf(.5f, 0f, 0f, 0f, 0f, .25f, 0f, 0f, 0f, 0f, .125f, 0f, 0f, 0f, 0f, 1f), values)
    }

    @Test
    fun normalProviderConflictAndInvalidLinearTransformsAreRejected() {
        val suppliedNormal = parameters("normalFromLocal" to byteArrayOf(1))
        assertFailsWith<IllegalArgumentException> { ParameterDerivations(true).resolve(suppliedNormal, Matrices.Identity) }
        for (scale in listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val transform = Matrices.fromScale(Vector(scale, 1.0, 1.0))
            assertFailsWith<IllegalArgumentException> { ParameterDerivations(true).resolve(ParameterValues(), transform) }
        }
        ParameterDerivations(true).resolve(ParameterValues(), Matrices.fromScale(Vector(1e-20, 1.0, 1.0)))
    }

    @Test
    fun wellConditionedUniformScalesAreIndependentOfAbsoluteMagnitude() {
        for (scale in listOf(1e-7, 1e7)) {
            val output = ParameterDerivations(true).resolve(ParameterValues(), Matrices.fromScale(Vector(scale, scale, scale)))
            val normal = ByteBuffer.wrap(output.requireNumeric(ParameterName("normalFromLocal")).bytes()).order(ByteOrder.nativeOrder()).asFloatBuffer()
            assertEquals((1.0 / scale).toFloat(), normal[0])
            assertEquals((1.0 / scale).toFloat(), normal[5])
            assertEquals((1.0 / scale).toFloat(), normal[10])
            assertEquals(1f, normal[15])
        }
    }

    @Test
    fun normalInverseTransposePreservesShearAndSignedScale() {
        val model = Matrices.of(
            -2.0, 1.0, 0.0, 0.0,
            0.0, 4.0, 0.0, 0.0,
            0.0, 0.0, 8.0, 0.0,
            0.0, 0.0, 0.0, 1.0,
        )
        val output = ParameterDerivations(true).resolve(ParameterValues(), model)
        val normal = ByteBuffer.wrap(output.requireNumeric(ParameterName("normalFromLocal")).bytes()).order(ByteOrder.nativeOrder()).asFloatBuffer()
        assertEquals(-.5f, normal[0])
        assertEquals(.125f, normal[1])
        assertEquals(0f, normal[4])
        assertEquals(.25f, normal[5])
        assertEquals(.125f, normal[10])
    }

    @Test
    fun dependentAxesAndFloatEncodingOverflowOrUnderflowAreRejected() {
        val dependent = Matrices.of(
            1.0, 2.0, 0.0, 0.0,
            2.0, 4.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0,
        )
        val overflow = Matrices.fromScale(Vector(1e-40, 1e40, 1.0))
        val underflow = Matrices.fromScale(Vector(1e300, 1.0, 1.0))
        for (model in listOf(dependent, overflow, underflow)) {
            assertFailsWith<IllegalArgumentException> { ParameterDerivations(true).resolve(ParameterValues(), model) }
        }
    }

    private fun field(name: String, offsetBytes: Int, sizeBytes: Int): PushConstantField = PushConstantField(ParameterName(name), offsetBytes, sizeBytes)

    private fun parameters(vararg entries: Pair<String, ByteArray>): ParameterValues =
        ParameterValues(entries.associate { [name, bytes] -> ParameterName(name) to NumericParameterValue(bytes) })
}
