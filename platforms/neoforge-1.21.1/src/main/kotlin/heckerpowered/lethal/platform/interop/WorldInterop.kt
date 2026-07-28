/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.requireHost
import net.minecraft.world.level.Level

fun WorldAccess.asHost() = requireHost<Level>(this)
