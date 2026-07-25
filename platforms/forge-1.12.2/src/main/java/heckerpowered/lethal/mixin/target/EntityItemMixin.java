/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.DroppedItemAccess;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityItem.class)
@Implements(@Interface(iface = DroppedItemAccess.class, prefix = "droppedItemAccess$"))
abstract class EntityItemMixin {
    @Shadow
    public abstract void setNoPickupDelay();

    public void droppedItemAccess$makeImmediatelyCollectible() {
        setNoPickupDelay();
    }
}
