/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.item.ItemBlueprint
import heckerpowered.bridge.adapter.item.ItemForm
import heckerpowered.bridge.adapter.item.ItemProperties
import heckerpowered.lethal.Constants

open class Ares protected constructor(identifierPath: String) : ItemBlueprint {
    companion object : Ares("ares")

    final override val identifier = Constants.identifier(identifierPath)
    final override val form: ItemForm = ItemForm.Sword
    final override val properties = ItemProperties(maxStackCount = 1)
}
