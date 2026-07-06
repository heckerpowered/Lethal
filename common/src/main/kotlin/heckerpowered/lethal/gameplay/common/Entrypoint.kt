/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common

import heckerpowered.lethal.bridge.platform.Services
import heckerpowered.lethal.bridge.platform.services.Entrypoint
import heckerpowered.lethal.gameplay.common.item.TestItem

class Entrypoint : Entrypoint {
    override fun onEntrypoint() {
        Services.ItemRegistration.register(TestItem())
    }
}