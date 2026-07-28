/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.PlayerAccess;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityPlayer.class)
@Implements(@Interface(iface = PlayerAccess.class, prefix = "playerAccess$"))
abstract class EntityPlayerMixin {
    @Shadow
    public abstract boolean isSpectator();

    @Shadow
    public abstract void addExperience(int amount);

    @Intrinsic
    public boolean playerAccess$isSpectator() {
        return isSpectator();
    }

    public void playerAccess$addExperiencePoints(int points) {
        addExperience(points);
    }
}
