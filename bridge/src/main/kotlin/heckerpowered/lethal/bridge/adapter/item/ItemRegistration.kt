/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.adapter.item

interface ItemRegistration {
    fun register(blueprint: ItemBlueprint): ItemAccess
}
