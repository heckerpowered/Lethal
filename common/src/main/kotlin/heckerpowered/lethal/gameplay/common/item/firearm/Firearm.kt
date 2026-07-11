/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.item.ItemBlueprint
import heckerpowered.bridge.adapter.item.ItemForm

abstract class Firearm : ItemBlueprint, ItemForm, Gun {
    override val form: ItemForm
        get() = this
}
