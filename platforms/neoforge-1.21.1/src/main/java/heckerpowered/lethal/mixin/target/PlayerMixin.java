/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.PlayerAccess;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Player.class)
@Implements(@Interface(iface = PlayerAccess.class, prefix = "playerAccess$"))
abstract class PlayerMixin {
    @Shadow
    public abstract boolean isSpectator();

    @Shadow
    public abstract void giveExperiencePoints(int points);

    @Intrinsic
    public boolean playerAccess$isSpectator() {
        return isSpectator();
    }

    public void playerAccess$addExperiencePoints(int points) {
        giveExperiencePoints(points);
    }
}
