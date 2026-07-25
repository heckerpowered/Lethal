/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.ExperienceOrbAccess;
import net.minecraft.entity.item.EntityXPOrb;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityXPOrb.class)
@Implements(@Interface(iface = ExperienceOrbAccess.class, prefix = "experienceOrbAccess$"))
abstract class EntityXPOrbMixin {
    @Unique
    private EntityXPOrb lethal$self() {
        return (EntityXPOrb) (Object) this;
    }

    public int experienceOrbAccess$getExperiencePoints() {
        return lethal$self().getXpValue();
    }

    public void experienceOrbAccess$consume() {
        lethal$self().setDead();
    }
}
