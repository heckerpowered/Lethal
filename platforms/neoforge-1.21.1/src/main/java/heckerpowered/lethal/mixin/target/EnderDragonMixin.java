/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityPartAccess;
import heckerpowered.bridge.adapter.entity.MultipartEntityAccess;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EnderDragon.class)
@Implements(@Interface(iface = MultipartEntityAccess.class, prefix = "multipartEntityAccess$"))
abstract class EnderDragonMixin {
    @Shadow
    @NotNull
    public abstract EnderDragonPart[] getSubEntities();

    @NotNull
    public EntityPartAccess @NotNull [] multipartEntityAccess$getParts() {
        return (EntityPartAccess[]) getSubEntities();
    }
}
