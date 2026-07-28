/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

import heckerpowered.bridge.adapter.effect.StatusEffectAccess
import heckerpowered.bridge.adapter.asView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntityAccessViewTest {
    @Test
    fun entityKindsExtendEntityAccess() {
        assertTrue(EntityAccess::class.java.isAssignableFrom(DroppedItemAccess::class.java))
        assertTrue(EntityAccess::class.java.isAssignableFrom(ExperienceOrbAccess::class.java))
        assertTrue(EntityAccess::class.java.isAssignableFrom(EntityPartAccess::class.java))
        assertTrue(EntityAccess::class.java.isAssignableFrom(MultipartEntityAccess::class.java))
    }

    @Test
    fun entityCapabilitiesRetainTheirStableLivingEntityBase() {
        assertTrue(LivingEntityAccess::class.java.isAssignableFrom(DeferredExperienceDropAccess::class.java))
        assertTrue(LivingEntityAccess::class.java.isAssignableFrom(EntityEquipmentAccess::class.java))
        assertTrue(LivingEntityAccess::class.java.isAssignableFrom(EntityExecutionAccess::class.java))
        assertTrue(LivingEntityAccess::class.java.isAssignableFrom(StatusEffectAccess::class.java))
    }

    @Test
    fun requiredAccessPreservesNonNullability() {
        val entity = createEntity(LivingEntityAccess::class.java)

        assertTrue(entity === entity.asView<LivingEntityAccess>())
        assertFailsWith<ClassCastException> { entity.asView<DroppedItemAccess>() }
    }

    @Test
    fun optionalAccessResolvesEntityKindsAndCapabilities() {
        val entity = createEntity(StatusEffectAccess::class.java)
        val absentEntity: EntityAccess? = null

        assertTrue(entity === entity.asView<LivingEntityAccess>())
        assertTrue(entity === entity.asView<StatusEffectAccess>())
        assertNull(entity as? DroppedItemAccess)
        assertNull(absentEntity?.asView<LivingEntityAccess>())
    }

    private fun createEntity(vararg interfaces: Class<*>): EntityAccess {
        return Proxy.newProxyInstance(EntityAccess::class.java.classLoader, interfaces) { _, method, _ ->
            error("Bridge view resolution must not invoke ${method.name}")
        } as EntityAccess
    }
}
