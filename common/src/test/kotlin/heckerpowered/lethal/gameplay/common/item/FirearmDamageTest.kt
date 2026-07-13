/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RayView
import heckerpowered.bridge.math.VectorView
import heckerpowered.lethal.gameplay.common.item.firearm.Firearm
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class FirearmDamageTest {
    @Test
    fun archaeopteryxMultipliesDamageWithoutRepeatingRaycast() {
        verifyBatchedDamage(Archaeopteryx, shotCount = 4, expectedDamagePoints = 28.0)
    }

    @Test
    fun fortuneMultipliesDamageWithoutRepeatingRaycast() {
        verifyBatchedDamage(Fortune, shotCount = 3, expectedDamagePoints = 90000.0)
    }

    private fun verifyBatchedDamage(firearm: Firearm, shotCount: Long, expectedDamagePoints: Double) {
        var receivedDamagePoints = 0.0
        val target = createTarget { damagePoints -> receivedDamagePoints = damagePoints }
        val world = TargetWorld(target)
        val player = createPlayer(world)
        val weaponStack = proxy<ItemStackAccess>()

        firearm.shoot(player, weaponStack, shotCount)

        assertEquals(expectedDamagePoints, receivedDamagePoints)
        assertEquals(1, world.raycastSourceCallCount)
    }

    private fun createPlayer(world: WorldAccess): PlayerAccess {
        return Proxy.newProxyInstance(PlayerAccess::class.java.classLoader, arrayOf(PlayerAccess::class.java)) { player, method, arguments ->
            when (method.name) {
                "getWorld" -> world
                "getEyePosition" -> Geometry.vector(0.0, 0.0, 0.0)
                "getViewVector" -> Geometry.vector(0.0, 0.0, 1.0)
                "hashCode" -> System.identityHashCode(player)
                "equals" -> player === arguments?.firstOrNull()
                else -> error("Unsupported PlayerAccess method: " + method.name)
            }
        } as PlayerAccess
    }

    private fun createTarget(recordDamage: (Double) -> Unit): EntityAccess {
        return Proxy.newProxyInstance(EntityAccess::class.java.classLoader, arrayOf(EntityAccess::class.java)) { target, method, arguments ->
            when (method.name) {
                "getBoundingBox" -> Geometry.box(-1.0, -1.0, 5.0, 1.0, 1.0, 6.0)
                "hurt" -> {
                    recordDamage(arguments?.get(1) as Double)
                    true
                }

                "hashCode" -> System.identityHashCode(target)
                "equals" -> target === arguments?.firstOrNull()
                else -> error("Unsupported EntityAccess method: " + method.name)
            }
        } as EntityAccess
    }

    private inline fun <reified Access : Any> proxy(): Access {
        return Proxy.newProxyInstance(Access::class.java.classLoader, arrayOf(Access::class.java)) { _, method, _ ->
            error("Unsupported " + Access::class.java.simpleName + " method: " + method.name)
        } as Access
    }

    private class TargetWorld(private val target: EntityAccess) : WorldAccess {
        var raycastSourceCallCount = 0
            private set

        override val isClientSide = false
        override val loadedEntityCount = 1
        override val entities: Sequence<EntityAccess>
            get() {
                raycastSourceCallCount++
                return sequenceOf(target)
            }

        override fun getEntities(searchBox: BoxView): Sequence<EntityAccess> {
            raycastSourceCallCount++
            return sequenceOf(target)
        }

        override fun getEntityRayBuckets(ray: RayView, length: Double): Sequence<EntityRayBucket> {
            raycastSourceCallCount++
            return sequenceOf(EntityRayBucket(0.0, listOf(target)))
        }

        override fun playSound(position: VectorView, playback: SoundPlayback) {
        }

        override fun spawnParticles(position: VectorView, effect: ParticleEffect) {
        }
    }
}
