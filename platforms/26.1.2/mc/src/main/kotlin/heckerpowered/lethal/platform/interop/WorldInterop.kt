/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.MixinInterop
import heckerpowered.bridge.adapter.world.WorldAccess
import net.minecraft.world.level.Level

fun Level.asView(): WorldAccess = MixinInterop.requireAccess<WorldAccess>(this)

fun WorldAccess.asHost(): Level = MixinInterop.requireHost<Level>(this)
