/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.form.mining

import heckerpowered.bridge.adapter.item.form.ItemForm

interface MiningTool : ItemForm {
    val miningCategory: MiningToolCategory
}
