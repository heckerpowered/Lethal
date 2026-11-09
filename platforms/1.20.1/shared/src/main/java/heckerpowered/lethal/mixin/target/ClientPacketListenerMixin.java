/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.lethal.mixin.impl.ClientCommandImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void lethal$consumeDiagnostic(String command, CallbackInfo callback) {
        ClientCommandImpl.consumeDiagnostic(command, callback);
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void lethal$consumeDiagnosticChat(String message, CallbackInfo callback) {
        ClientCommandImpl.consumeDiagnosticChat(message, callback);
    }

    @Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
    private void lethal$consumeUnsignedDiagnostic(String command, CallbackInfoReturnable<Boolean> callback) {
        ClientCommandImpl.consumeUnsignedDiagnostic(command, callback);
    }
}
