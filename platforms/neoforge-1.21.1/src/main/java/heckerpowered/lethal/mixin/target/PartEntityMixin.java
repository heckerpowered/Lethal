/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.entity.EntityPartAccess;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PartEntity.class)
@Implements(@Interface(iface = EntityPartAccess.class, prefix = "entityPartAccess$"))
abstract class PartEntityMixin {
    @Shadow
    @NotNull
    public abstract Entity getParent();

    @NotNull
    public EntityAccess entityPartAccess$getParent() {
        return MixinInterop.requireAccess(getParent(), EntityAccess.class);
    }
}
