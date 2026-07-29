/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RayView
import heckerpowered.bridge.math.VectorView
import heckerpowered.lethal.gameplay.common.item.firearm.Firearm
import heckerpowered.lethal.gameplay.common.sound.ModSounds
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FirearmSoundTest {
    @Test
    fun firearmsPlayTheirOwnSoundsAtThePlayerEyePosition() {
        val world = RecordingWorld()
        val eyePosition = Geometry.vector(1.0, 2.0, 3.0)
        val weaponStack = createWeaponStack()
        val player = createPlayer(world, eyePosition, weaponStack)

        val expectedSounds = listOf<Pair<Firearm, SoundPlayback>>(
            Fortune to ModSounds.FortuneFire,
            Archaeopteryx to ModSounds.ArchaeopteryxFire,
            EnhancedFortune to ModSounds.FortuneFire,
            Chaos to ModSounds.ChaosFire,
            Zeus to ModSounds.ZeusFire,
            ZeusGolden to ModSounds.ZeusFire,
            ZeusBlackGold to ModSounds.ZeusFire,
            ZeusGlowSquid to ModSounds.ZeusFire,
            ZeusSculk to ModSounds.ZeusFire,
        )
        expectedSounds.forEach { (firearm, _) ->
            firearm.shoot(player, weaponStack, 1)
        }

        assertEquals(expected = expectedSounds.size, actual = world.playedSounds.size)
        expectedSounds.zip(world.playedSounds).forEach { (expected, playedSound) ->
            assertSame(eyePosition, playedSound.position)
            assertSame(expected.second, playedSound.playback)
        }
    }

    private fun createPlayer(world: WorldAccess, eyePosition: VectorView, weaponStack: ItemStackAccess): PlayerAccess {
        return Proxy.newProxyInstance(
            PlayerAccess::class.java.classLoader,
            arrayOf(PlayerAccess::class.java, EntityEquipmentAccess::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getWorld" -> world
                "getEyePosition" -> eyePosition
                "getViewVector" -> Geometry.vector(0.0, 0.0, 1.0)
                "getEquippedStack" -> weaponStack
                else -> error("Unsupported PlayerAccess method: " + method.name)
            }
        } as PlayerAccess
    }

    private fun createWeaponStack(): ItemStackAccess {
        return Proxy.newProxyInstance(ItemStackAccess::class.java.classLoader, arrayOf(ItemStackAccess::class.java)) { _, method, _ ->
            error("Unsupported ItemStackAccess method: " + method.name)
        } as ItemStackAccess
    }

    private class RecordingWorld : WorldAccess {
        val playedSounds = mutableListOf<PlayedSound>()

        override val isClientSide = false
        override val loadedEntityCount = 0
        override val entities = emptySequence<EntityAccess>()

        override fun getEntities(searchBox: BoxView): Sequence<EntityAccess> {
            return emptySequence()
        }

        override fun getEntityRayBuckets(ray: RayView, length: Double): Sequence<EntityRayBucket> {
            return emptySequence()
        }

        override fun raycastBlockHits(ray: RayView, distanceBlocks: Double, shape: BlockRaycastShape): Sequence<BlockHitResult> {
            return emptySequence()
        }

        override fun destroyBlock(position: BlockPositionView, dropItems: Boolean): Boolean {
            return false
        }

        override fun playSound(position: VectorView, playback: SoundPlayback) {
            playedSounds += PlayedSound(position, playback)
        }

        override fun spawnParticles(position: VectorView, effect: ParticleEffect) {
        }
    }

    private data class PlayedSound(val position: VectorView, val playback: SoundPlayback)
}
