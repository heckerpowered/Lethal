/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import heckerpowered.bridge.adapter.item.ItemBlueprint
import heckerpowered.bridge.adapter.item.ItemProvider

class HostingItemProvider : ItemProvider {
    override fun item(blueprint: ItemBlueprint): HostedItem {
        return HostedItem(blueprint)
    }
}
