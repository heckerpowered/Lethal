/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services;

import heckerpowered.bridge.adapter.client.MinecraftAccess;
import heckerpowered.bridge.platform.services.ClientPlatform;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

public final class NeoForgeClientPlatform implements ClientPlatform {
    @Override
    @NotNull
    public MinecraftAccess getMinecraft() {
        return MixinInterop.requireAccess(Minecraft.getInstance(), MinecraftAccess.class);
    }
}
