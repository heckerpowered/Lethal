/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.PlayerAccess;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;

@Mixin(Player.class)
@Implements(@Interface(iface = PlayerAccess.class, prefix = "playerAccess$"))
abstract class PlayerMixin {
    @Shadow
    public abstract boolean isSpectator();

    @Shadow
    public abstract void giveExperiencePoints(int i);

    @Intrinsic
    public boolean playerAccess$isSpectator() {
        return isSpectator();
    }

    public void playerAccess$addExperiencePoints(int points) {
        giveExperiencePoints(points);
    }
}
