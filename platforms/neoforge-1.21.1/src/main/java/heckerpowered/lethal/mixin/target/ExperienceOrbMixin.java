/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.ExperienceOrbAccess;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExperienceOrb.class)
@Implements(@Interface(iface = ExperienceOrbAccess.class, prefix = "experienceOrbAccess$"))
abstract class ExperienceOrbMixin {
    @Shadow
    public abstract int getValue();

    public int experienceOrbAccess$getExperiencePoints() {
        return getValue();
    }
}
