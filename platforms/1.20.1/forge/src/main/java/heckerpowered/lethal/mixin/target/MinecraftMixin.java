/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import heckerpowered.bridge.adapter.client.MinecraftAccess;
import heckerpowered.bridge.adapter.entity.ClientPlayerAccess;
import heckerpowered.lethal.mixin.impl.MinecraftAccessImpl;
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
    @WrapMethod(method = "runTick")
    private void lethal$performanceFrame(boolean renderLevel, Operation<Void> original) {
        MinecraftAccessImpl.performanceFrame(renderLevel, original);
    }

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Nullable
    public ClientPlayerAccess minecraftAccess$getPlayer() {
        return MinecraftAccessImpl.player(player);
    }
}
