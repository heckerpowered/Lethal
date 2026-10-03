/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.math.Vector
import heckerpowered.math.VectorView
import java.lang.reflect.Proxy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShotgunSpreadTest {
    @Test
    fun zeroSpreadSamplesTheViewDirectionThroughShoot() {
        val forward = Vector(0.0, 1.0, 0.0)
        val gun = SpreadGun(12, 0.0)
        gun.fire(forward)
        assertEquals(12, gun.directions.size)
        gun.directions.forEach { assertEquals(1.0, it.dot(forward), 1.0e-12) }
    }

    @Test
    fun sampledDirectionsStayInTheConeWhenLookingStraightUp() {
        val forward = Vector(0.0, 1.0, 0.0)
        val angle = 6.0 * PI / 180.0
        val gun = SpreadGun(512, angle)
        gun.fire(forward)
        gun.directions.forEach { direction ->
            assertEquals(1.0, direction.dot(direction), 1.0e-12)
            assertTrue(direction.dot(forward) >= cos(angle) - 1.0e-12)
            assertTrue(direction.dot(forward) <= 1.0 + 1.0e-12)
        }
    }

    @Test
    fun shootSamplesUnitDirectionsUniformlyInSolidAngle() {
        val forward = Vector(0.0, 0.0, 1.0)
        val angle = 6.0 * PI / 180.0
        val gun = SpreadGun(20_000, angle)
        gun.fire(forward)
        val cosines = gun.directions.map { direction ->
            assertEquals(1.0, direction.dot(direction), 1.0e-12)
            val cosine = direction.dot(forward)
            assertTrue(cosine >= cos(angle) - 1.0e-12 && cosine <= 1.0 + 1.0e-12)
            cosine
        }
        assertEquals((1.0 + cos(angle)) / 2.0, cosines.average(), 0.00015)
    }

    @Test
    fun invalidSpreadFailsBeforeAnyWorldQuery() {
        for (angle in listOf(-0.1, Double.NaN, PI)) {
            val gun = SpreadGun(12, angle)
            assertFailsWith<IllegalArgumentException> { gun.fire(Vector(0.0, 0.0, 1.0)) }
            assertEquals(0, gun.queryCalls)
            assertTrue(gun.directions.isEmpty())
        }
    }

    private class SpreadGun(private val pellets: Int, private val angle: Double) : Shotgun() {
        override val identifier: Identifier = Constants.identifier("test_spread")
        val directions = mutableListOf<VectorView>()
        var queryCalls = 0
        override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency = Frequency.perSecond(2)
        override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double = 15.0
        override fun getPelletCount(player: PlayerAccess, weaponStack: ItemStackAccess): Int = pellets
        override fun getSpreadAngle(player: PlayerAccess, weaponStack: ItemStackAccess): Double = angle
        override fun samplePelletDirection(forward: VectorView, spreadAngle: Double): VectorView {
            return super.samplePelletDirection(forward, spreadAngle).also { directions += it }
        }
        override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
            assertTrue(entityHits.toList().isEmpty())
        }

        fun fire(forward: VectorView) {
            val world = proxy<WorldAccess> { name, _ -> when (name) {
                "getLoadedEntityCount" -> { queryCalls++; 0 }
                "getEntities" -> emptySequence<heckerpowered.bridge.adapter.entity.EntityAccess>()
                "getEntityRayBuckets" -> emptySequence<EntityRayBucket>()
                else -> error("Unexpected world call: $name")
            } }
            val player = proxy<PlayerAccess> { name, _ -> when (name) {
                "getId" -> 1
                "getEyePosition" -> Vector(0.0, 1.0, 0.0)
                "getViewVector" -> forward
                "getWorld" -> world
                else -> error("Unexpected player call: $name")
            } }
            val stack = proxy<ItemStackAccess> { name, _ -> error("Unexpected stack call: $name") }
            fire(player, stack)
        }
    }
}

private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, arguments ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === arguments?.firstOrNull()
            else -> call(method.name, arguments ?: emptyArray())
        }
    } as T
