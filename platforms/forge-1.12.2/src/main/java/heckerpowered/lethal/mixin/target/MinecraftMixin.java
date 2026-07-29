/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.client.MinecraftAccess;
import heckerpowered.bridge.adapter.entity.ClientPlayerAccess;
import heckerpowered.lethal.platform.interop.ObjectInterop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.*;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
@Implements(@Interface(iface = MinecraftAccess.class, prefix = "minecraftAccess$"))
class MinecraftMixin {
    @Shadow
    public EntityPlayerSP player;

    @Intrinsic
    @Nullable
    public ClientPlayerAccess minecraftAccess$getPlayer() {
        return (ClientPlayerAccess) ObjectInterop.asView(player);
    }
}
