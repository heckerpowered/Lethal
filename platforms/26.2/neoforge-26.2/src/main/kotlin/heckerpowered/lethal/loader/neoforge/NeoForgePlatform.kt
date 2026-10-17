/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.neoforge

import heckerpowered.bridge.platform.services.ModPlatform
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLLoader

class NeoForgePlatform : ModPlatform {
    override val platformName: String get() = "NeoForge"

    override fun isModLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)

    override val isDevelopmentEnvironment: Boolean get() = !FMLLoader.getCurrent().isProduction
}
