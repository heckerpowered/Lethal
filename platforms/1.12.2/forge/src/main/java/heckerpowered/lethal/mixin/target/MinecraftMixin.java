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
import heckerpowered.lethal.platform.interop.ObjectInterop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
@Implements(@Interface(iface = MinecraftAccess.class, prefix = "minecraftAccess$"))
class MinecraftMixin {
    @Shadow
    public EntityPlayerSP player;

    @WrapMethod(method = "runGameLoop")
    private void lethal$performanceFrame(Operation<Void> original) {
        MinecraftAccessImpl.performanceFrame(original);
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;destroy()V", remap = false))
    private void releaseGraphicsResources(CallbackInfo callback) {
        MinecraftAccessImpl.releaseGraphicsResources();
    }

    @Intrinsic
    @Nullable
    public ClientPlayerAccess minecraftAccess$getPlayer() {
        return (ClientPlayerAccess) ObjectInterop.asViewOrNull(player);
    }
}
