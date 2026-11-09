/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common

import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent

open class CommonProxy {
    open fun preInitialize(event: FMLPreInitializationEvent) {
    }

    open fun initialize(event: FMLInitializationEvent) {
    }

    open fun postInitialize(event: FMLPostInitializationEvent) {
    }
}