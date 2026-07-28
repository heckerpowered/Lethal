/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityPartAccess;
import heckerpowered.bridge.adapter.entity.MultipartEntityAccess;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.boss.EntityDragon;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityDragon.class)
@Implements(@Interface(iface = MultipartEntityAccess.class, prefix = "multipartEntityAccess$"))
abstract class EntityDragonMixin {
    @Shadow
    public MultiPartEntityPart[] dragonPartArray;

    @NotNull
    public EntityPartAccess[] multipartEntityAccess$getParts() {
        // MultiPartEntityPartMixin makes every native part implement EntityPartAccess before this
        // method runs. The native array can therefore be reused directly.
        return (EntityPartAccess[]) dragonPartArray;
    }
}
