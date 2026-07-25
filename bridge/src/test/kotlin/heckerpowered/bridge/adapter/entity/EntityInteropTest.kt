/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntityInteropTest {
    @Test
    fun entityKindsExtendEntityAccess() {
        assertTrue(EntityAccess::class.java.isAssignableFrom(DroppedItemAccess::class.java))
        assertTrue(EntityAccess::class.java.isAssignableFrom(ExperienceOrbAccess::class.java))
        assertTrue(EntityAccess::class.java.isAssignableFrom(EntityPartAccess::class.java))
        assertTrue(EntityAccess::class.java.isAssignableFrom(MultipartEntityAccess::class.java))
    }

    @Test
    fun optionalAbilitiesRemainIndependentFromEntityAccess() {
        assertFalse(EntityAccess::class.java.isAssignableFrom(GlowingAccess::class.java))
        assertFalse(EntityAccess::class.java.isAssignableFrom(EntityRemovalAccess::class.java))
    }

    @Test
    fun supportedEntityTypesAndCapabilitiesResolveToTheOriginalEntity() {
        val entity = createEntity(
            ServerPlayerAccess::class.java,
            DeferredExperienceDropAccess::class.java,
            EntityEquipmentAccess::class.java,
            EntityExecutionAccess::class.java,
            EntityRemovalAccess::class.java,
            ExperienceOrbAccess::class.java,
            ExperienceReceiverAccess::class.java,
            GlowingAccess::class.java,
            DroppedItemAccess::class.java,
            EntityPartAccess::class.java,
            MultipartEntityAccess::class.java,
            SpectatorAccess::class.java,
        )

        assertTrue(entity === EntityInterop.deferredExperienceDrops(entity))
        assertTrue(entity === EntityInterop.equipment(entity))
        assertTrue(entity === EntityInterop.execution(entity))
        assertTrue(entity === EntityInterop.removal(entity))
        assertTrue(entity === EntityInterop.experienceOrb(entity))
        assertTrue(entity === EntityInterop.experienceReceiver(entity))
        assertTrue(entity === EntityInterop.glowing(entity))
        assertTrue(entity === EntityInterop.droppedItem(entity))
        assertTrue(entity === EntityInterop.living(entity))
        assertTrue(entity === EntityInterop.multipart(entity))
        assertTrue(entity === EntityInterop.part(entity))
        assertTrue(entity === EntityInterop.serverPlayer(entity))
        assertTrue(entity === EntityInterop.spectator(entity))
    }

    @Test
    fun unsupportedEntityTypesAndCapabilitiesResolveToNull() {
        val entity = createEntity(EntityAccess::class.java)

        assertNull(EntityInterop.deferredExperienceDrops(entity))
        assertNull(EntityInterop.equipment(entity))
        assertNull(EntityInterop.execution(entity))
        assertNull(EntityInterop.removal(entity))
        assertNull(EntityInterop.experienceOrb(entity))
        assertNull(EntityInterop.experienceReceiver(entity))
        assertNull(EntityInterop.glowing(entity))
        assertNull(EntityInterop.droppedItem(entity))
        assertNull(EntityInterop.living(entity))
        assertNull(EntityInterop.multipart(entity))
        assertNull(EntityInterop.part(entity))
        assertNull(EntityInterop.serverPlayer(entity))
        assertNull(EntityInterop.spectator(entity))
    }

    private fun createEntity(vararg interfaces: Class<*>): EntityAccess {
        return Proxy.newProxyInstance(EntityAccess::class.java.classLoader, interfaces) { _, method, _ ->
            error("EntityInterop must not invoke ${method.name} while resolving capabilities")
        } as EntityAccess
    }
}
