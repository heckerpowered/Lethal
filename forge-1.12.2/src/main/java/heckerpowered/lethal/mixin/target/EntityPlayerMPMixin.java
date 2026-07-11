/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.ServerPlayerAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityPlayerMP.class)
@Implements(@Interface(iface = ServerPlayerAccess.class, prefix = "serverPlayerAccess$"))
class EntityPlayerMPMixin {
}
