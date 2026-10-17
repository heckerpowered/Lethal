/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.forge

import heckerpowered.bridge.platform.services.ModPlatform
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader

class ForgePlatform : ModPlatform {
    override val platformName: String get() = "Forge"

    override fun isModLoaded(modId: String): Boolean = ModList.isLoaded(modId)

    override val isDevelopmentEnvironment: Boolean get() = !FMLLoader.isProduction()
}
