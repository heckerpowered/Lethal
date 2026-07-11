/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay

import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.platform.services.Entrypoint
import heckerpowered.lethal.gameplay.common.CommonProxy
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent

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

        @SidedProxy(
            serverSide = "heckerpowered.lethal.gameplay.common.CommonProxy",
            clientSide = "heckerpowered.lethal.gameplay.client.ClientProxy"
        )
        @JvmStatic
        lateinit var proxy: CommonProxy

        @JvmStatic
        fun resource(path: String): ResourceLocation {
            return ResourceLocation(MOD_ID, path)
        }
    }

    @EventHandler
    fun preInitialize(event: FMLPreInitializationEvent) {
        proxy.preInitialize(event)
        Services.callEntrypoints<Entrypoint>()
    }


    @EventHandler
    fun initialize(event: FMLInitializationEvent) {
        proxy.initialize(event)
    }

    @EventHandler
    fun postInitialize(event: FMLPostInitializationEvent) {
        proxy.postInitialize(event)
    }

}
