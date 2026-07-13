/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.creativetab

class RecordingCreativeModeTabRegistrar : CreativeModeTabRegistrar {
    val tabs = mutableListOf<CreativeModeTabBlueprint>()

    override fun register(blueprint: CreativeModeTabBlueprint): CreativeModeTabAccess {
        tabs += blueprint
        return blueprint
    }
}
