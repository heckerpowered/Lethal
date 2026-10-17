/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.fabric

import heckerpowered.bridge.platform.services.ModPlatform
import net.fabricmc.loader.api.FabricLoader

class FabricPlatform : ModPlatform {
    override val platformName: String get() = "Fabric"

    override fun isModLoaded(modId: String): Boolean = FabricLoader.getInstance().isModLoaded(modId)

    override val isDevelopmentEnvironment: Boolean get() = FabricLoader.getInstance().isDevelopmentEnvironment
}
