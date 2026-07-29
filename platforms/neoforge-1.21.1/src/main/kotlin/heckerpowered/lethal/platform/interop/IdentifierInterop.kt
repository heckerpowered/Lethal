/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.requireAccess
import heckerpowered.bridge.requireHost
import heckerpowered.bridge.resources.Identifier
import net.minecraft.resources.ResourceLocation

fun Identifier.asHost() = requireHost<ResourceLocation>(this)

fun ResourceLocation.asView() = requireAccess<Identifier>(this)
