/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.entity.EntityPartAccess;
import heckerpowered.lethal.platform.interop.ObjectInterop;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.MultiPartEntityPart;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MultiPartEntityPart.class)
@Implements(@Interface(iface = EntityPartAccess.class, prefix = "entityPartAccess$"))
abstract class MultiPartEntityPartMixin {
    @Shadow
    @Final
    public IEntityMultiPart parent;

    @NotNull
    public EntityAccess entityPartAccess$getParent() {
        if (!(parent instanceof Entity)) {
            throw new IllegalStateException("Multipart entity parent is not an entity: " + parent.getClass().getName());
        }

        return ObjectInterop.entity((Entity) parent);
    }
}
