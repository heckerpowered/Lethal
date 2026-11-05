/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.OwnedEntityAccess;
import net.minecraft.world.entity.TamableAnimal;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.UUID;

@Mixin(TamableAnimal.class)
@Implements(@Interface(iface = OwnedEntityAccess.class, prefix = "ownedEntityAccess$"))
abstract class TamableAnimalMixin {
    @Shadow
    @Nullable
    public abstract UUID getOwnerUUID();

    @Nullable
    public UUID ownedEntityAccess$getOwnerUuid() {
        return getOwnerUUID();
    }
}
