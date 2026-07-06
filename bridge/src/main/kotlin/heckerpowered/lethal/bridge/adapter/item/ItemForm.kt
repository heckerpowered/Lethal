/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.adapter.item

sealed interface ItemForm {
    object Regular : ItemForm

    sealed interface MiningTool : ItemForm {
        val miningCategory: MiningToolCategory
    }

    object Pickaxe : MiningTool {
        override val miningCategory: MiningToolCategory
            get() = MiningToolCategory.Pickaxe
    }

    object Axe : MiningTool {
        override val miningCategory: MiningToolCategory
            get() = MiningToolCategory.Axe
    }

    object Shovel : MiningTool {
        override val miningCategory: MiningToolCategory
            get() = MiningToolCategory.Shovel
    }

    object Hoe : MiningTool {
        override val miningCategory: MiningToolCategory
            get() = MiningToolCategory.Hoe
    }

    object Sword : ItemForm

    sealed interface Armor : ItemForm {
        val equipmentSlot: EquipmentSlot
    }

    object Helmet : Armor {
        override val equipmentSlot: EquipmentSlot
            get() = EquipmentSlot.Head
    }

    object Chestplate : Armor {
        override val equipmentSlot: EquipmentSlot
            get() = EquipmentSlot.Chest
    }

    object Leggings : Armor {
        override val equipmentSlot: EquipmentSlot
            get() = EquipmentSlot.Legs
    }

    object Boots : Armor {
        override val equipmentSlot: EquipmentSlot
            get() = EquipmentSlot.Feet
    }
}

enum class MiningToolCategory {
    Pickaxe,
    Axe,
    Shovel,
    Hoe,
}

enum class EquipmentSlot {
    MainHand,
    OffHand,
    Feet,
    Legs,
    Chest,
    Head,
}

enum class Hand {
    Main,
    Off,
}

enum class ItemUseAnimation {
    None,
    Eat,
    Drink,
    Block,
    Bow,
    Spear,
}

enum class ItemInteractionResult {
    Pass,
    Success,
    Consume,
    Fail,
}
