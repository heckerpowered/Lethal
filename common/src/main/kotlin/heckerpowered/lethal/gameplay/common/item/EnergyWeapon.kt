/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.lethal.gameplay.common.item.firearm.WeaponEnergy

interface EnergyWeapon {
    val energy: WeaponEnergy
}

fun ItemAccess.energyWeapon(): EnergyWeapon? {
    return this as? EnergyWeapon ?: form as? EnergyWeapon
}
