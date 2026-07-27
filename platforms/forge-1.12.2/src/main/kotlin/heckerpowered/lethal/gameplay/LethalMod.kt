/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay

import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.platform.services.Entrypoint
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.CommonProxy
import heckerpowered.lethal.platform.adapter.item.creativetab.ForgeCreativeModeTabs
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent

@Mod(
    modid = Constants.MOD_ID,
    name = "Lethal Mod"
)
class LethalMod {
    companion object {
        @SidedProxy(
            serverSide = "heckerpowered.lethal.gameplay.common.CommonProxy",
            clientSide = "heckerpowered.lethal.gameplay.client.ClientProxy"
        )
        @JvmStatic
        lateinit var proxy: CommonProxy
    }

    @EventHandler
    fun preInitialize(event: FMLPreInitializationEvent) {
        proxy.preInitialize(event)
        Services.callEntrypoints<Entrypoint>()
        ForgeCreativeModeTabs.initialize()
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
