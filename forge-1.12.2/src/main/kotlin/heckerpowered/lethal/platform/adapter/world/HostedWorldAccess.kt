/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.world

import heckerpowered.lethal.bridge.adapter.world.WorldAccess
import net.minecraft.world.World

class HostedWorldAccess(val world: World) : WorldAccess
