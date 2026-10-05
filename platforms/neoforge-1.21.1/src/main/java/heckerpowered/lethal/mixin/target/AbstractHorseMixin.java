/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.OwnedEntityAccess;
import heckerpowered.lethal.mixin.impl.OwnedEntityAccessImpl;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;

import java.util.UUID;

@Mixin(AbstractHorse.class)
@Implements(@Interface(iface = OwnedEntityAccess.class, prefix = "ownedEntityAccess$"))
abstract class AbstractHorseMixin {
    @Nullable
    public UUID ownedEntityAccess$getOwnerUuid() {
        return OwnedEntityAccessImpl.ownerUuid((AbstractHorse) (Object) this);
    }
}
