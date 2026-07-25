/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.ExperienceReceiverAccess;
import heckerpowered.bridge.adapter.entity.PlayerAccess;
import heckerpowered.bridge.adapter.entity.SpectatorAccess;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Player.class)
@Implements({
        @Interface(iface = PlayerAccess.class, prefix = "playerAccess$"),
        @Interface(iface = SpectatorAccess.class, prefix = "spectatorAccess$"),
        @Interface(iface = ExperienceReceiverAccess.class, prefix = "experienceReceiverAccess$")
})
abstract class PlayerMixin {
    @Shadow
    public abstract boolean isSpectator();

    @Shadow
    public abstract void giveExperiencePoints(int points);

    @Intrinsic
    public boolean spectatorAccess$isSpectator() {
        return isSpectator();
    }

    public void experienceReceiverAccess$addExperiencePoints(int points) {
        giveExperiencePoints(points);
    }
}
