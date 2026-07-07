/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.world

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.adapter.world.WorldAccess
import heckerpowered.lethal.bridge.math.BoxView
import heckerpowered.lethal.mixin.impl.WorldAccessImpl
import net.minecraft.world.World

class HostedWorldAccess(val world: World) : WorldAccess {
    override val entities: Sequence<EntityAccess>
        get() = WorldAccessImpl.entities(world)

    override fun getEntities(searchBox: BoxView): Sequence<EntityAccess> {
        return WorldAccessImpl.getEntities(world, searchBox)
    }
}
