/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item.creativetab

import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabAccess
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabBlueprint
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabRegistrar

class HostingCreativeModeTabRegistrar : CreativeModeTabRegistrar {
    override fun register(blueprint: CreativeModeTabBlueprint): CreativeModeTabAccess {
        return HostedCreativeModeTab(blueprint)
    }
}
