/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.entity.EntityAccess
import net.minecraft.world.entity.Entity

fun Entity.entity() = requireAccess<EntityAccess>(this)

fun EntityAccess.entity() = requireHost<Entity>(this)

fun EntityAccess?.entityOrNull() = this?.entity()
