/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal

import heckerpowered.bridge.resources.Identifier

object Constants {
    const val MOD_ID = "lethal"

    fun identifier(path: String): Identifier {
        return Identifier.create(MOD_ID, path)
    }
}
