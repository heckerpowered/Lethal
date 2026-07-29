/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.client.MinecraftAccess;
import heckerpowered.bridge.adapter.entity.ClientPlayerAccess;
import heckerpowered.bridge.MixinInterop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Minecraft.class)
@Implements(@Interface(iface = MinecraftAccess.class, prefix = "minecraftAccess$"))
abstract class MinecraftMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @Nullable
    public ClientPlayerAccess minecraftAccess$getPlayer() {
        if (player == null) {
            return null;
        }

        return MixinInterop.requireAccess(player, ClientPlayerAccess.class);
    }
}
