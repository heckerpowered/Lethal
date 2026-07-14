/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.adapter.client.render.ClientPostProcessContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BloomTestTest {
    @AfterTest
    fun restoreDefaults() {
        BloomTest.Enabled = false
        BloomTest.BrightnessThreshold = 0.7F
    }

    @Test
    fun `enabled Bloom uses the configured brightness threshold`() {
        val context = RecordingPostProcessContext(isBloomSupported = true)
        BloomTest.Enabled = true
        BloomTest.BrightnessThreshold = 0.6F

        BloomTest.onPostProcess(context)

        assertEquals(0.6F, context.appliedBrightnessThreshold)
    }

    @Test
    fun `disabled Bloom skips post processing`() {
        val context = RecordingPostProcessContext(isBloomSupported = true)
        BloomTest.Enabled = false

        BloomTest.onPostProcess(context)

        assertNull(context.appliedBrightnessThreshold)
    }

    @Test
    fun `unsupported Bloom skips post processing`() {
        val context = RecordingPostProcessContext(isBloomSupported = false)
        BloomTest.Enabled = true

        BloomTest.onPostProcess(context)

        assertNull(context.appliedBrightnessThreshold)
    }
}

private class RecordingPostProcessContext(override val isBloomSupported: Boolean) : ClientPostProcessContext {
    var appliedBrightnessThreshold: Float? = null
        private set

    override fun applyBloomToScene(brightnessThreshold: Float) {
        appliedBrightnessThreshold = brightnessThreshold
    }

    override fun renderContentWithBloom(brightnessThreshold: Float, renderContent: () -> Unit) {
        error("Bloom content rendering is not expected in this test")
    }
}
