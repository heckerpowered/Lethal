/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common

import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.platform.services.Entrypoint
import heckerpowered.lethal.gameplay.common.item.TestItem

class Entrypoint : Entrypoint {
    override fun onEntrypoint() {
        Services.ItemRegistrar.register(TestItem())
    }
}