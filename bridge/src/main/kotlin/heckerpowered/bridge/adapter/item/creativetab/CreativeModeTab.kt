/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.creativetab

import heckerpowered.bridge.adapter.item.ItemBlueprint
import heckerpowered.bridge.resources.Identifier
import java.util.*

class CreativeModeTabBlueprint(val identifier: Identifier, val titleTranslationKey: String, val icon: ItemBlueprint, val items: List<ItemBlueprint>) {
    init {
        require(titleTranslationKey.isNotBlank()) { "Creative mode tab title translation key must not be blank" }

        val uniqueItems = Collections.newSetFromMap(IdentityHashMap<ItemBlueprint, Boolean>())
        require(this.items.all(uniqueItems::add)) { "Creative mode tab items must not contain duplicate blueprints" }
    }
}
