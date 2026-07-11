/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.platform.services.ClientEntrypoint

class Entrypoint : ClientEntrypoint {
    override fun onEntrypoint() {
        MouseEventHandler.onInitialize()
    }
}