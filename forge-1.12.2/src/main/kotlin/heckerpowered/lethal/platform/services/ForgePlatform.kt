/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.lethal.bridge.platform.services.ModPlatform
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.relauncher.FMLLaunchHandler

class ForgePlatform : ModPlatform {
    /**
     * The name of current platform, e.g. "Fabric", "Forge", "Paper", etc.
     */
    override val platformName: String
        get() = "Forge"

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return `true` if the mod is loaded, `false` otherwise.
     */
    override fun isModLoaded(modId: String): Boolean {
        return Loader.isModLoaded(modId)
    }

    /**
     * Check if the game is currently in a development environment.
     */
    override val isDevelopmentEnvironment: Boolean
        get() = FMLLaunchHandler.isDeobfuscatedEnvironment()
}