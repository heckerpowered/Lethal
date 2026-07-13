/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.creativetab

interface CreativeModeTabRegistrar {
    fun register(blueprint: CreativeModeTabBlueprint): CreativeModeTabAccess
}
