/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.lethal.platform.command.MinecraftClientCommands
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

object ClientCommandImpl {
    @JvmStatic
    fun consumeDiagnostic(message: String, callback: CallbackInfo) {
        if (MinecraftClientCommands.consumeChat(message)) callback.cancel()
    }
}
