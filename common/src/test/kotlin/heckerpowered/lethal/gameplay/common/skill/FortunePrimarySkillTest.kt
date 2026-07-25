/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.block.BlockCategory
import heckerpowered.bridge.adapter.block.BlockClassificationAccess
import heckerpowered.bridge.adapter.block.BlockAccess
import heckerpowered.bridge.adapter.block.BlockStateAccess
import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.GlowingAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.item.stack.PersistentDataAccess
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.BlockPositions
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RayView
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.gameplay.common.item.EnhancedFortune
import heckerpowered.lethal.gameplay.common.item.Fortune
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FortunePrimarySkillTest {
    @Test
    fun fortuneWeaponsShareTheMigratedPrimaryAndSecondarySkills() {
        val primarySkill = assertNotNull(Fortune.getSkill(SkillSlot.Primary))
        val secondarySkill = assertNotNull(Fortune.getSkill(SkillSlot.Secondary))
        val ultimateSkill = assertNotNull(Fortune.getSkill(SkillSlot.Ultimate))
        val enhancedUltimateSkill = assertNotNull(EnhancedFortune.getSkill(SkillSlot.Ultimate))

        assertSame(primarySkill, EnhancedFortune.getSkill(SkillSlot.Primary))
        assertSame(secondarySkill, EnhancedFortune.getSkill(SkillSlot.Secondary))
        assertNotSame(ultimateSkill, enhancedUltimateSkill)
        assertSame(fortuneUltimateSkill, ultimateSkill)
        assertSame(enhancedFortuneUltimateSkill, enhancedUltimateSkill)
    }

    @Test
    fun activationStartsCooldownAndMakesNearbySupportedEntitiesGlow() {
        var currentTime = 1_000L
        val skill = FortunePrimarySkill { currentTime }
        val stack = TestItemStack()
        val world = RecordingWorld()
        val playerGlowDurations = mutableListOf<Int>()
        val firstTargetGlowDurations = mutableListOf<Int>()
        val secondTargetGlowDurations = mutableListOf<Int>()
        val playerBounds = Geometry.box(0.0, 1.0, 2.0, 1.0, 3.0, 3.0)
        val player = createPlayer(1, world, playerBounds, playerGlowDurations)
        world.nearbyEntities = listOf(
            player,
            createEntity(2, glowDurations = firstTargetGlowDurations),
            createEntity(3),
            createEntity(4, glowDurations = secondTargetGlowDurations),
        )

        skill.activate(player, stack)

        assertEquals(listOf(1_200), firstTargetGlowDurations)
        assertEquals(listOf(1_200), secondTargetGlowDurations)
        assertEquals(emptyList(), playerGlowDurations)
        assertEquals(31_000L, stack.storedLongs.single())
        val searchBox = requireNotNull(world.lastSearchBox)
        assertEquals(-64.0, searchBox.minX)
        assertEquals(-63.0, searchBox.minY)
        assertEquals(-62.0, searchBox.minZ)
        assertEquals(65.0, searchBox.maxX)
        assertEquals(67.0, searchBox.maxY)
        assertEquals(67.0, searchBox.maxZ)

        currentTime = 30_999L
        skill.activate(player, stack)
        assertEquals(listOf(1_200), firstTargetGlowDurations)

        currentTime = 31_000L
        skill.activate(player, stack)
        assertEquals(listOf(1_200, 1_200), firstTargetGlowDurations)
    }

    @Test
    fun fortunePresentationCapabilitiesExposeSkillStatusAndActiveGlint() {
        val stack = TestItemStack()
        val world = RecordingWorld()
        val player = createPlayer(1, world, Geometry.box(0.0, 0.0, 0.0, 1.0, 2.0, 1.0))

        assertFalse(Fortune.hasGlint(stack))
        assertFalse(EnhancedFortune.hasGlint(stack))
        assertEquals(4, Fortune.getTooltipLines(stack).size)
        assertEquals(4, EnhancedFortune.getTooltipLines(stack).size)

        fortunePrimarySkill.activate(player, stack)

        assertTrue(Fortune.hasGlint(stack))
        assertTrue(EnhancedFortune.hasGlint(stack))
    }

    @Test
    fun penetrationUsesTheLegacyNinetySecondWindow() {
        var currentTime = 1_000L
        val skill = FortunePrimarySkill { currentTime }
        val stack = TestItemStack()
        val world = RecordingWorld()
        val player = createPlayer(1, world, Geometry.box(0.0, 0.0, 0.0, 1.0, 2.0, 1.0))

        assertFalse(skill.isActive(stack))
        skill.activate(player, stack)

        currentTime = 91_000L
        assertTrue(skill.isActive(stack))

        currentTime = 91_001L
        assertFalse(skill.isActive(stack))
    }

    @Test
    fun activePenetrationDestroysOnlyReplaceableBlocksAndSugarCaneWithoutDrops() {
        var currentTime = 1_000L
        val skill = FortunePrimarySkill { currentTime }
        val stack = TestItemStack()
        val world = RecordingWorld()
        val player = createPlayer(1, world, Geometry.box(0.0, 0.0, 0.0, 1.0, 2.0, 1.0))
        skill.activate(player, stack)

        val replaceablePosition = BlockPositions.of(1, 2, 3)
        val sugarCanePosition = BlockPositions.of(4, 5, 6)
        assertFalse(skill.isRayBlockedBy(player, stack, blockHit(replaceablePosition, TestBlockState(isReplaceable = true))))
        assertFalse(skill.isRayBlockedBy(player, stack, blockHit(sugarCanePosition, TestBlockState(categories = setOf(BlockCategory.SugarCane)))))

        assertEquals(
            listOf(
                DestroyedBlock(replaceablePosition, dropItems = false),
                DestroyedBlock(sugarCanePosition, dropItems = false),
            ),
            world.destroyedBlocks,
        )

        for (category in listOf(BlockCategory.Logs, BlockCategory.Planks, BlockCategory.Snow, BlockCategory.Sand)) {
            assertFalse(skill.isRayBlockedBy(player, stack, blockHit(BlockPositions.Zero, TestBlockState(categories = setOf(category)))))
        }
        assertFalse(skill.isRayBlockedBy(player, stack, blockHit(BlockPositions.Zero, TestBlockState(identifierPath = "dirt"))))
        assertTrue(skill.isRayBlockedBy(player, stack, blockHit(BlockPositions.Zero, TestBlockState(identifierPath = "stone"))))
        assertTrue(skill.isRayBlockedBy(player, stack, blockHit(BlockPositions.Zero, OrdinaryBlockState)))

        currentTime = 91_001L
        assertTrue(skill.isRayBlockedBy(player, stack, blockHit(BlockPositions.Zero, TestBlockState(categories = setOf(BlockCategory.Logs)))))
    }

    private fun blockHit(position: BlockPositionView, blockState: BlockStateAccess): BlockHitResult {
        return BlockHitResult(position, blockState, BlockDirection.North, Geometry.vector(0.0, 0.0, 0.0), 0.0)
    }

    private fun createPlayer(
        id: Int,
        world: WorldAccess,
        boundingBox: BoxView,
        glowDurations: MutableList<Int>? = null,
    ): PlayerAccess {
        return createEntity(id, PlayerAccess::class.java, glowDurations) { methodName ->
            when (methodName) {
                "getWorld" -> world
                "getBoundingBox" -> boundingBox
                else -> UnhandledMethod
            }
        } as PlayerAccess
    }

    private fun createEntity(
        id: Int,
        primaryInterface: Class<out EntityAccess> = EntityAccess::class.java,
        glowDurations: MutableList<Int>? = null,
        additionalMethod: (String) -> Any? = { UnhandledMethod },
    ): EntityAccess {
        val interfaces = mutableListOf<Class<*>>(primaryInterface)
        if (glowDurations != null) interfaces += GlowingAccess::class.java

        return Proxy.newProxyInstance(EntityAccess::class.java.classLoader, interfaces.toTypedArray()) { entity, method, arguments ->
            when (method.name) {
                "getId" -> id
                "glowFor" -> glowDurations?.add(arguments?.first() as Int)
                "hashCode" -> System.identityHashCode(entity)
                "equals" -> entity === arguments?.firstOrNull()
                else -> additionalMethod(method.name).takeUnless { result -> result === UnhandledMethod }
                    ?: error("Unsupported entity method: ${method.name}")
            }
        } as EntityAccess
    }

    private class TestItemStack : ItemStackAccess, PersistentDataAccess {
        private val longValues = mutableMapOf<Identifier, Long>()

        val storedLongs: Collection<Long>
            get() = longValues.values

        override val item: ItemAccess
            get() = error("Item is not used by this test")
        override var count = 1
        override var damagePoints = 0
        override val maxStackCount = 1
        override val maxDamagePoints = 0

        override fun getLong(key: Identifier): Long? = longValues[key]

        override fun setLong(key: Identifier, value: Long) {
            longValues[key] = value
        }

        override fun getDouble(key: Identifier): Double? = null

        override fun setDouble(key: Identifier, value: Double) {
        }

        override fun remove(key: Identifier) {
            longValues.remove(key)
        }
    }

    private data class TestBlockState(
        val identifierPath: String = "test_block",
        override val isReplaceable: Boolean = false,
        val categories: Set<BlockCategory> = emptySet(),
    ) : BlockStateAccess, BlockClassificationAccess {
        override val block = TestBlock(Identifier.create("minecraft", identifierPath))

        override fun isIn(category: BlockCategory): Boolean {
            return category in categories
        }
    }

    private object OrdinaryBlockState : BlockStateAccess {
        override val block = TestBlock(Identifier.create("test", "ordinary"))
    }

    private data class TestBlock(override val identifier: Identifier) : BlockAccess

    private data class DestroyedBlock(val position: BlockPositionView, val dropItems: Boolean)

    private class RecordingWorld : WorldAccess {
        var nearbyEntities = emptyList<EntityAccess>()
        var lastSearchBox: BoxView? = null
        val destroyedBlocks = mutableListOf<DestroyedBlock>()

        override val isClientSide = false
        override val loadedEntityCount: Int
            get() = nearbyEntities.size
        override val entities: Sequence<EntityAccess>
            get() = nearbyEntities.asSequence()

        override fun getEntities(searchBox: BoxView): Sequence<EntityAccess> {
            lastSearchBox = searchBox
            return nearbyEntities.asSequence()
        }

        override fun getEntityRayBuckets(ray: RayView, length: Double): Sequence<EntityRayBucket> = emptySequence()

        override fun raycastBlockHits(
            ray: RayView,
            distanceBlocks: Double,
            shape: BlockRaycastShape,
        ): Sequence<BlockHitResult> = emptySequence()

        override fun destroyBlock(position: BlockPositionView, dropItems: Boolean): Boolean {
            destroyedBlocks += DestroyedBlock(position, dropItems)
            return true
        }

        override fun playSound(position: VectorView, playback: SoundPlayback) {
        }

        override fun spawnParticles(position: VectorView, effect: ParticleEffect) {
        }
    }

    private companion object {
        object UnhandledMethod
    }
}
