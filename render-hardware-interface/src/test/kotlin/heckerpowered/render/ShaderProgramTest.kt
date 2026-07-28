/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShaderProgramTest {
    @Test
    fun selectsTheFirstSupportedVariant() {
        val specialized = variant("specialized", GraphicsFeature.NativeUniformBuffers)
        val fallback = variant("fallback")
        val program = ShaderProgram(specialized, fallback)

        assertEquals(expected = specialized, actual = program.select(GraphicsCapabilities(true, true, true, true)))
        assertEquals(expected = fallback, actual = program.select(GraphicsCapabilities(true, true, true, false)))
    }

    @Test
    fun requiresItsFallbackToBeLast() {
        assertFailsWith<IllegalArgumentException> { ShaderProgram(variant("fallback"), variant("specialized", GraphicsFeature.NativeUniformBuffers)) }
    }

    private fun variant(label: String, vararg requiredFeatures: GraphicsFeature): ShaderProgramVariant {
        return ShaderProgramVariant(
            ShaderSource.text("void main() {}", label = "$label vertex"),
            ShaderSource.text("void main() {}", label = "$label fragment"),
            *requiredFeatures,
        )
    }
}
