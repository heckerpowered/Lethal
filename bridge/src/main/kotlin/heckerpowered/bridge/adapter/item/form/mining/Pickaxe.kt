/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.form.mining

object Pickaxe : MiningTool {
    override val miningCategory: MiningToolCategory
        get() = MiningToolCategory.Pickaxe
}
