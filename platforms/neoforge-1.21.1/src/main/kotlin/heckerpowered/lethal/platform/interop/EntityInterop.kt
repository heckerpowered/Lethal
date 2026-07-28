/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.requireAccess
import heckerpowered.bridge.requireHost
import net.minecraft.world.entity.Entity

fun Entity.asView() = requireAccess<EntityAccess>(this)

fun EntityAccess.asHost() = requireHost<Entity>(this)
