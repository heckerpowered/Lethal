/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.lethal.gameplay.common.item.firearm.RayTraceGun
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class WeaponRangeTest {
    @Test
    fun ak103ReachesTwentyEightBlocks() {
        assertEquals(28.0, rangeBlocks(Archaeopteryx))
    }

    @Test
    fun scarletFoxReachesTwentyEightBlocks() {
        assertEquals(28.0, rangeBlocks(Fortune))
    }

    @Test
    fun zeusVariantsRetainTheirOwnRanges() {
        for ((weapon, expectedRange) in listOf(Zeus to 70.0, ZeusGolden to 70.0, ZeusBlackGold to 80.0, ZeusSculk to 96.0, ZeusGlowSquid to 70.0)) {
            assertEquals(expectedRange, rangeBlocks(weapon), weapon.identifier.asString())
        }
    }

    private fun rangeBlocks(weapon: RayTraceGun): Double {
        val owner = generateSequence<Class<*>>(weapon.javaClass) { it.superclass }.first { type ->
            type.declaredMethods.any { it.name == "getRayTraceDistanceBlocks" }
        }
        val method = owner.getDeclaredMethod("getRayTraceDistanceBlocks", PlayerAccess::class.java, ItemStackAccess::class.java)
        method.isAccessible = true
        return method.invoke(weapon, unusedArgument<PlayerAccess>(), unusedArgument<ItemStackAccess>()) as Double
    }

    private inline fun <reified T> unusedArgument(): T {
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ ->
            error("Fixed weapon range must not depend on player or stack state")
        } as T
    }
}
