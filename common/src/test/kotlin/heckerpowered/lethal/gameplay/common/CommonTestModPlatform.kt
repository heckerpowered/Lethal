/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common

import heckerpowered.bridge.platform.services.ModPlatform

class CommonTestModPlatform : ModPlatform {
    override val platformName = "Common Test"
    override val isDevelopmentEnvironment = false

    override fun isModLoaded(modId: String): Boolean {
        return false
    }
}
