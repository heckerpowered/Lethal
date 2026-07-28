/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.platform.services.ClientEntrypoint
import heckerpowered.lethal.gameplay.client.render.ForgeEntityRenderers
import heckerpowered.lethal.gameplay.common.CommonProxy
import heckerpowered.lethal.platform.input.ForgeKeyBindings
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent

class ClientProxy : CommonProxy() {
    override fun preInitialize(event: FMLPreInitializationEvent) {
        super.preInitialize(event)
        ForgeEntityRenderers.onInitialize()
        Services.callEntrypoints<ClientEntrypoint>()
    }

    override fun initialize(event: FMLInitializationEvent) {
        super.initialize(event)
        ForgeKeyBindings.registerAll()
    }
}
