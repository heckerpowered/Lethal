/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.ClientPlayerAccess;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityPlayerSP.class)
@Implements(@Interface(iface = ClientPlayerAccess.class, prefix = "clientPlayerAccess$"))
public class EntityPlayerSPMixin {
}
