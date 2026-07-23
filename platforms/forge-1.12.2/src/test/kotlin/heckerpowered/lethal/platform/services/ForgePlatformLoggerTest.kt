/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.platform.services.PlatformLogger
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertIs

class ForgePlatformLoggerTest {
    @Test
    fun `service loader finds Forge platform logger`() {
        val platformLogger = ServiceLoader.load(PlatformLogger::class.java)
            .firstOrNull()

        assertIs<ForgePlatformLogger>(platformLogger)
    }
}
