/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.platform.services.ClientPlatform
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertIs

class ForgeClientPlatformTest {
    @Test
    fun loadsForgeClientPlatformProvider() {
        val provider = ServiceLoader.load(ClientPlatform::class.java)
            .firstOrNull()

        assertIs<ForgeClientPlatform>(provider)
    }
}
