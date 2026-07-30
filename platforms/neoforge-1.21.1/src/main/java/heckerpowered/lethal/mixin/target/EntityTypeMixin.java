/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.MixinInterop;
import heckerpowered.bridge.adapter.entity.EntityTypeAccess;
import heckerpowered.bridge.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityType.class)
@Implements(@Interface(iface = EntityTypeAccess.class, prefix = "entityTypeAccess$"))
abstract class EntityTypeMixin {
    @NotNull
    public Identifier entityTypeAccess$getIdentifier() {
        ResourceLocation identifier = BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) (Object) this);
        return MixinInterop.requireAccess(identifier, Identifier.class);
    }
}
