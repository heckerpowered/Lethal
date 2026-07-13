/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabAccess
import heckerpowered.lethal.platform.adapter.item.creativetab.HostedCreativeModeTab
import net.minecraft.creativetab.CreativeTabs

object CreativeModeTabInterop {
    @JvmStatic
    fun creativeModeTab(tab: CreativeModeTabAccess): CreativeTabs {
        if (tab is HostedCreativeModeTab) return tab

        @Suppress("CAST_NEVER_SUCCEEDS")
        return tab as? CreativeTabs ?: error("Unsupported CreativeModeTabAccess implementation: ${tab.javaClass.name}")
    }
}

fun CreativeModeTabAccess.creativeModeTab(): CreativeTabs {
    return CreativeModeTabInterop.creativeModeTab(this)
}
