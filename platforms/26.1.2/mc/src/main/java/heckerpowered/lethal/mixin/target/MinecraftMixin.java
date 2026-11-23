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
import heckerpowered.lethal.platform.MinecraftInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
@Implements(@Interface(iface = MinecraftAccess.class, prefix = "minecraftAccess$"))
abstract class MinecraftMixin {
    @Shadow
    public LocalPlayer player;

    @WrapMethod(method = "runTick")
    private void lethal$performanceFrame(boolean advanceGameTime, Operation<Void> original) {
        MinecraftAccessImpl.performanceFrame(advanceGameTime, original);
    }

    @Nullable
    public ClientPlayerAccess minecraftAccess$getPlayer() {
        return MinecraftAccessImpl.player(player);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void lethal$disconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo callback) {
        MinecraftInput.onControlSessionEnded();
    }
}
