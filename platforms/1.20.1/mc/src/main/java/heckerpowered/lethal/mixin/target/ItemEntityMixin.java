/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.DroppedItemAccess;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemEntity.class)
@Implements(@Interface(iface = DroppedItemAccess.class, prefix = "droppedItemAccess$"))
abstract class ItemEntityMixin {
    @Shadow
    public abstract void setNoPickUpDelay();

    public void droppedItemAccess$makeImmediatelyCollectible() {
        setNoPickUpDelay();
    }
}
