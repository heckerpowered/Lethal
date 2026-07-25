/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.lethal.mixin.impl.LevelEntityIndex;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelEntityGetterAdapter.class)
abstract class LevelEntityGetterAdapterMixin implements LevelEntityIndex {
    @Shadow
    @Final
    private EntityLookup<?> visibleEntities;

    @Override
    public int lethal$getLoadedEntityCount() {
        return visibleEntities.count();
    }
}
