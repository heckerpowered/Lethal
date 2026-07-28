/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.event

import heckerpowered.bridge.adapter.sound.SoundRegistry
import heckerpowered.lethal.Constants
import heckerpowered.lethal.platform.interop.asHost
import net.minecraft.util.SoundEvent
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

@Mod.EventBusSubscriber(modid = Constants.MOD_ID)
object SoundRegistrationEventHandler {
    @SubscribeEvent
    @JvmStatic
    fun onRegisterSounds(event: RegistryEvent.Register<SoundEvent>) {
        for ((soundIdentifier) in SoundRegistry.all()) {
            val identifier = soundIdentifier.asHost()
            event.registry.register(SoundEvent(identifier).setRegistryName(identifier))
        }
    }
}
