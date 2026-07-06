/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay

import heckerpowered.lethal.bridge.platform.Services
import heckerpowered.lethal.bridge.platform.loads
import heckerpowered.lethal.bridge.platform.services.Entrypoint
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.Logger

@Mod(
    modid = LethalMod.MOD_ID,
    name = LethalMod.NAME,
    version = LethalMod.VERSION,
    // modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter"
)
class LethalMod {
    companion object {
        const val MOD_ID = "lethal"
        const val NAME = "Lethal Mod"
        const val VERSION = "1.0"

        @JvmStatic
        lateinit var Logger: Logger

        @JvmStatic
        fun resource(path: String): ResourceLocation {
            return ResourceLocation(MOD_ID, path)
        }
    }

    @EventHandler
    fun preInitialize(event: FMLPreInitializationEvent) {
        Logger = event.modLog

        val entrypoints = Services.loads<Entrypoint>()
        val entrypointCount = entrypoints.count()

        Logger.info("Found $entrypointCount entrypoint(s).")
        for ((index, entrypoint) in entrypoints.withIndex()) {
            Logger.info("Calling entrypoint ($index/$entrypointCount): ${entrypoint.javaClass.name}")
            entrypoint.onEntrypoint()
        }
    }
}