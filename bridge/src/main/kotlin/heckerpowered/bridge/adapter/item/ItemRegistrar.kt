/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

interface ItemRegistrar {
    fun register(blueprint: ItemBlueprint): ItemAccess
}
