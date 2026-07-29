/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.EquipmentSlot
import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.ItemForm
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.all
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class WeaponSkillActivationTest {
    @Test
    fun compatibleSkillsInBothHandsReceiveTheRequestInHandOrder() {
        val activatedStacks = mutableListOf<ItemStackAccess>()
        val skill = RecordingSkill(activatedStacks)
        val mainHandStack = TestItemStack(TestItem(TestSkillWeapon(mapOf(SkillSlot.Primary to skill))))
        val offHandStack = TestItemStack(TestItem(TestSkillWeapon(mapOf(SkillSlot.Primary to skill))))
        val player = createPlayer(mainHandStack, offHandStack)
        WeaponSkillActivation.onInitialize()

        SkillActivation.handle(SkillActivationRequest(player, SkillSlot.Primary))

        assertEquals(expected = listOf<ItemStackAccess>(mainHandStack, offHandStack), actual = activatedStacks)
        assertSame(player, skill.lastPlayer)
    }

    @Test
    fun requestActivatesOnlyTheSelectedSlotAndSkipsOrdinaryItems() {
        val primaryActivations = mutableListOf<ItemStackAccess>()
        val secondaryActivations = mutableListOf<ItemStackAccess>()
        val skillWeapon = TestSkillWeapon(
            mapOf(
                SkillSlot.Primary to RecordingSkill(primaryActivations),
                SkillSlot.Secondary to RecordingSkill(secondaryActivations),
            ),
        )
        val weaponStack = TestItemStack(TestItem(skillWeapon))
        val ordinaryStack = TestItemStack(TestItem(ItemForm.Regular))
        val player = createPlayer(weaponStack, ordinaryStack)
        WeaponSkillActivation.onInitialize()

        SkillActivation.handle(SkillActivationRequest(player, SkillSlot.Secondary))

        assertEquals(expected = emptyList<ItemStackAccess>(), actual = primaryActivations)
        assertEquals(expected = listOf<ItemStackAccess>(weaponStack), actual = secondaryActivations)
    }

    @Test
    fun invalidPlayerStateCannotActivateWeaponSkills() {
        WeaponSkillActivation.onInitialize()

        assertActivationCount(false, false, false, 0)
        assertActivationCount(true, true, false, 0)
        assertActivationCount(true, false, true, 0)
        assertActivationCount(true, false, false, 1)
    }

    @Test
    fun repeatedInitializationRegistersOneActivationRule() {
        WeaponSkillActivation.onInitialize()
        WeaponSkillActivation.onInitialize()

        val registrationCount = RuleRegistry.all<SkillActivationRule>()
            .count { rule -> rule === WeaponSkillActivation }
        assertEquals(expected = 1, actual = registrationCount)
    }

    private fun assertActivationCount(isAlive: Boolean, isRemoved: Boolean, isSpectator: Boolean, expectedCount: Int) {
        val activations = mutableListOf<ItemStackAccess>()
        val stack = TestItemStack(TestItem(TestSkillWeapon(mapOf(SkillSlot.Primary to RecordingSkill(activations)))))
        val player = createPlayer(stack, EmptyStack, isAlive, isRemoved, isSpectator)

        SkillActivation.handle(SkillActivationRequest(player, SkillSlot.Primary))

        assertEquals(expected = expectedCount, actual = activations.size)
    }

    private fun createPlayer(mainHandStack: ItemStackAccess, offHandStack: ItemStackAccess, isAlive: Boolean = true, isRemoved: Boolean = false, isSpectator: Boolean = false): PlayerAccess {
        return Proxy.newProxyInstance(PlayerAccess::class.java.classLoader, arrayOf(PlayerAccess::class.java, EntityEquipmentAccess::class.java)) { player, method, arguments ->
            when (method.name) {
                "getEquippedStack" -> when (arguments?.firstOrNull() as EquipmentSlot) {
                    EquipmentSlot.MainHand -> mainHandStack
                    EquipmentSlot.OffHand -> offHandStack
                    else -> error("Unsupported equipment slot")
                }

                "isAlive" -> isAlive
                "isRemoved" -> isRemoved
                "isSpectator" -> isSpectator
                "hashCode" -> System.identityHashCode(player)
                "equals" -> player === arguments?.firstOrNull()
                "toString" -> "WeaponSkillActivationTestPlayer"
                else -> error("Unsupported PlayerAccess method: ${method.name}")
            }
        } as PlayerAccess
    }

    private class RecordingSkill(private val activatedStacks: MutableList<ItemStackAccess>) : WeaponSkill {
        var lastPlayer: PlayerAccess? = null

        override fun activate(player: PlayerAccess, weaponStack: ItemStackAccess) {
            lastPlayer = player
            activatedStacks += weaponStack
        }
    }

    private class TestSkillWeapon(private val skills: Map<SkillSlot, WeaponSkill>) : ItemForm, SkillWeapon {
        override fun getSkill(slot: SkillSlot): WeaponSkill? {
            return skills[slot]
        }
    }

    private class TestItem(override val form: ItemForm) : ItemAccess {
        override val identifier: Identifier
            get() = error("Identifier is not used by this test")
    }

    private class TestItemStack(override val item: ItemAccess, override var count: Int = 1, override var damagePoints: Int = 0, override val maxStackCount: Int = 1, override val maxDamagePoints: Int = 0) : ItemStackAccess

    private companion object {
        val EmptyStack = TestItemStack(TestItem(ItemForm.Regular), 0)
    }
}
