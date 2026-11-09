/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.lethal.mixin.impl.ClientCommandImpl;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
    private void lethal$consumeUnattendedDiagnostic(String command, Screen screenAfterCommand, CallbackInfo callback) {
        ClientCommandImpl.consumeDiagnostic(command, callback);
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void lethal$consumeDiagnostic(String command, CallbackInfo callback) {
        ClientCommandImpl.consumeDiagnostic(command, callback);
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void lethal$consumeDiagnosticChat(String content, CallbackInfo callback) {
        ClientCommandImpl.consumeDiagnosticChat(content, callback);
    }
}
