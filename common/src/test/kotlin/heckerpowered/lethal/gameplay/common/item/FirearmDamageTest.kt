/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.block.BlockAccess
import heckerpowered.bridge.adapter.block.BlockStateAccess
import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.entity.DeferredExperienceDropAccess
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess
import heckerpowered.bridge.adapter.entity.EntityExecutionAccess
import heckerpowered.bridge.adapter.entity.EntityPartAccess
import heckerpowered.bridge.adapter.entity.DroppedItemAccess
import heckerpowered.bridge.adapter.entity.ExperienceOrbAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.MultipartEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.item.stack.PersistentDataAccess
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.AlgorithmResolution
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.adapter.world.raycast.RaycastExecutionPolicy
import heckerpowered.bridge.adapter.world.raycast.raycastEntityHits
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.BlockPositions
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RayView
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.gameplay.common.item.firearm.Firearm
import heckerpowered.lethal.gameplay.common.item.firearm.EntityDamageResult
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitDamage
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitEffect
import heckerpowered.lethal.gameplay.common.item.firearm.RayTraceGun
import heckerpowered.lethal.gameplay.common.network.RecordingPayloadTransport
import heckerpowered.lethal.gameplay.common.network.ZeusChainPayload
import heckerpowered.lethal.gameplay.common.network.ZeusChainSegment
import heckerpowered.lethal.gameplay.common.skill.FortuneSonicBoom
import heckerpowered.lethal.gameplay.common.skill.FortuneUltimateSkill
import heckerpowered.lethal.gameplay.common.skill.SkillSlot
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirearmDamageTest {
    @Test
    fun archaeopteryxMultipliesDamageWithoutRepeatingRaycast() {
        verifyBatchedDamage(Archaeopteryx, 4, 28.0)
    }

    @Test
    fun fortuneMultipliesDamageWithoutRepeatingRaycast() {
        verifyBatchedDamage(Fortune, 3, 90000.0)
    }

    @Test
    fun migratedLegacyWeaponsUseConfiguredDamageWithoutRepeatingRaycast() {
        verifyBatchedDamage(EnhancedFortune, 3, 126_000.0)
        verifyBatchedDamage(Chaos, 2, 60_000.0)
    }

    @Test
    fun entityHitDamageReportsTheClampedHealthDelta() {
        var damageResult: EntityDamageResult? = null
        val target = createTarget(2, targetBox(), true, 40.0) { _ -> }
        val world = TargetWorld(listOf(target))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)
        val hit = world
            .raycastEntityHits(Geometry.ray(player.eyePosition, player.viewVector), 20.0, player)
            .single()
        val hitDamage = EntityHitDamage(
            VanillaDamageType.Generic,
            1_000.0,
            EntityHitEffect { result -> damageResult = result },
        )

        hitDamage.apply(player, weaponStack, 1, sequenceOf(hit))

        assertEquals(expected = 1_000.0, actual = damageResult?.requestedDamagePoints)
        assertEquals(expected = 40.0, actual = damageResult?.actualDamagePoints)
        assertEquals(expected = true, actual = damageResult?.damageAccepted)
    }

    @Test
    fun fortuneFamilyChargesSonicBoomFromActualHealthLost() {
        for (firearm in listOf(Fortune, EnhancedFortune)) {
            val target = createTarget(2, targetBox(), true, 73.0) { _ -> }
            val world = TargetWorld(listOf(target))
            val weaponStack = PersistentTestItemStack()
            val player = createPlayer(world, weaponStack)

            firearm.shoot(player, weaponStack, 1)

            assertEquals(expected = 73.0, actual = FortuneSonicBoom.currentCharge(weaponStack))
            val ultimateSkill = firearm.getSkill(SkillSlot.Ultimate) as FortuneUltimateSkill
            assertEquals(expected = 73.0, actual = ultimateSkill.currentCharge(weaponStack))
        }
    }

    @Test
    fun legacyRarityHeadshotThresholdsExecuteWithAnAttributedPlayerSource() {
        val weaponStack = proxy<ItemStackAccess>()
        val eyePosition = Geometry.vector(0.0, 0.2, 0.0)
        val targetBox = Geometry.box(-0.25, -1.0, 5.0, 0.25, 1.0, 5.5)

        var fortuneExecutionSource: DamageSourceView? = null
        val fortuneTarget = createTarget(2, targetBox, true, DEFAULT_TARGET_HEALTH, false, { source -> fortuneExecutionSource = source }) { _ -> }
        val fortuneWorld = TargetWorld(listOf(fortuneTarget))
        val fortunePlayer = createPlayer(fortuneWorld, weaponStack, eyePosition)

        var chaosExecutionCount = 0
        val chaosTarget = createTarget(3, targetBox, true, DEFAULT_TARGET_HEALTH, false, { _ -> chaosExecutionCount++ }) { _ -> }
        val chaosWorld = TargetWorld(listOf(chaosTarget))
        val chaosPlayer = createPlayer(chaosWorld, weaponStack, eyePosition)

        Fortune.shoot(fortunePlayer, weaponStack, 1)
        Chaos.shoot(chaosPlayer, weaponStack, 1)

        val executionSource = requireNotNull(fortuneExecutionSource)
        assertTrue(executionSource.directEntity === fortunePlayer)
        assertTrue(executionSource.causingEntity === fortunePlayer)
        assertEquals(expected = VanillaDamageType.PlayerAttack.identifier, actual = executionSource.type)
        assertEquals(expected = 0, actual = chaosExecutionCount)
    }

    @Test
    fun zeusFinishesEntityQueryBeforeCapturingExperienceAndDroppedItems() {
        var consumedExperienceOrb = false
        var receivedExperiencePoints = 0
        var deferredExperienceReceiver: PlayerAccess? = null
        var madeItemImmediatelyCollectible = false
        var droppedItemDestination: VectorView? = null
        val nearbyEntities = mutableListOf<EntityAccess>()
        val experienceOrb = createExperienceOrb(3, 17) { consumedExperienceOrb = true }
        val droppedItem = createDroppedItem(4, { madeItemImmediatelyCollectible = true }) { position ->
            droppedItemDestination = position
            nearbyEntities.remove(experienceOrb)
        }
        val target = createTarget(2, targetBox(), true, 20.0, true, {}, { receiver -> deferredExperienceReceiver = receiver }) { _ ->
            nearbyEntities += experienceOrb
            nearbyEntities += droppedItem
        }
        val world = TargetWorld(listOf(target)) { nearbyEntities.asSequence() }
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack) { points -> receivedExperiencePoints += points }

        Zeus.shoot(player, weaponStack, 1)

        assertTrue(consumedExperienceOrb)
        assertEquals(expected = 17, actual = receivedExperiencePoints)
        assertTrue(deferredExperienceReceiver === player)
        assertTrue(madeItemImmediatelyCollectible)
        assertEquals(expected = player.position, actual = droppedItemDestination)
    }

    @Test
    fun zeusDoesNotClaimDeferredExperienceFromASurvivingTarget() {
        var deferredCaptureCount = 0
        val target = createTarget(2, targetBox(), true, DEFAULT_TARGET_HEALTH, true, {}, { _ -> deferredCaptureCount++ }) { _ -> }
        val world = TargetWorld(listOf(target))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        Zeus.shoot(player, weaponStack, 1)

        assertEquals(expected = 0, actual = deferredCaptureCount)
    }

    @Test
    fun migratedLegacyWeaponsUseConfiguredRayTraceDistances() {
        verifyRayTraceDistance(EnhancedFortune, 100.0)
        verifyRayTraceDistance(Chaos, 100.0)
    }

    @Test
    fun migratedLegacyWeaponsUseConfiguredFrequencies() {
        val world = TargetWorld(emptyList())
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        assertEquals(expected = Frequency.perMinute(840), actual = EnhancedFortune.getFrequency(player, weaponStack))
        assertEquals(expected = Frequency.perMinute(600), actual = Chaos.getFrequency(player, weaponStack))
    }

    @Test
    fun zeusFamilyUsesConfiguredDamageWithoutRepeatingRaycast() {
        verifyBatchedDamage(Zeus, 2, 400_000.0)
        verifyBatchedDamage(ZeusGolden, 2, 3_000_000.0)
        verifyBatchedDamage(ZeusBlackGold, 1, 12_000_000.0)
        verifyBatchedDamage(ZeusGlowSquid, 1, 10_000_000.0)
        verifyBatchedDamage(ZeusSculk, 1, 18_000_000.0)
    }

    @Test
    fun zeusFamilyUsesConfiguredRayTraceDistances() {
        verifyRayTraceDistance(Zeus, 70.0)
        verifyRayTraceDistance(ZeusGolden, 70.0)
        verifyRayTraceDistance(ZeusBlackGold, 90.0)
        verifyRayTraceDistance(ZeusGlowSquid, 70.0)
        verifyRayTraceDistance(ZeusSculk, 90.0)
    }

    @Test
    fun zeusFamilyUsesConfiguredChainDistances() {
        verifyChainDistance(Zeus, 2.0)
        verifyChainDistance(ZeusGlowSquid, 2.0)
        verifyChainDistance(ZeusGolden, 4.0)
        verifyChainDistance(ZeusBlackGold, 4.0)
        verifyChainDistance(ZeusSculk, 5.0)
    }

    @Test
    fun blockHitPreventsDamageToTargetsBehindIt() {
        var receivedDamagePoints = 0.0
        val target = createTarget(2, targetBox()) { damagePoints -> receivedDamagePoints = damagePoints }
        val world = TargetWorld(listOf(target), Geometry.vector(0.0, 0.0, 4.0))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        Fortune.shoot(player, weaponStack, 1)

        assertEquals(expected = 0.0, actual = receivedDamagePoints)
        assertEquals(expected = 1, actual = world.blockRaycastCallCount)
        assertEquals(expected = listOf(BlockRaycastShape.Collision), actual = world.blockRaycastShapes)
    }

    @Test
    fun blockChecksAdvanceBetweenEntityHitsAndStopAtObstruction() {
        val damagedEntityIds = mutableListOf<Int>()
        val firstTarget = createTarget(2, Geometry.box(-1.0, -1.0, 2.0, 1.0, 1.0, 3.0)) { _ -> damagedEntityIds += 2 }
        val secondTarget = createTarget(3, Geometry.box(-1.0, -1.0, 6.0, 1.0, 1.0, 7.0)) { _ -> damagedEntityIds += 3 }
        val thirdTarget = createTarget(4, Geometry.box(-1.0, -1.0, 10.0, 1.0, 1.0, 11.0)) { _ -> damagedEntityIds += 4 }
        val world = TargetWorld(listOf(firstTarget, secondTarget, thirdTarget), Geometry.vector(0.0, 0.0, 4.0))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        Fortune.shoot(player, weaponStack, 1)

        assertEquals(expected = listOf(2), actual = damagedEntityIds)
        assertEquals(expected = listOf(2.0, 4.0), actual = world.blockRaycastDistances)
    }

    @Test
    fun blockedRayDoesNotConsumeLaterEntityBuckets() {
        AlgorithmResolution.clear()
        val firstTarget = createTarget(2, Geometry.box(-1.0, -1.0, 2.0, 1.0, 1.0, 3.0)) { _ -> }
        val secondTarget = createTarget(3, Geometry.box(-1.0, -1.0, 6.0, 1.0, 1.0, 7.0)) { _ -> }
        val thirdTarget = createTarget(4, Geometry.box(-1.0, -1.0, 10.0, 1.0, 1.0, 11.0)) { _ -> }
        val world = TargetWorld(
            listOf(firstTarget, secondTarget, thirdTarget),
            Geometry.vector(0.0, 0.0, 1.0),
            listOf(
                EntityRayBucket(2.0, listOf(firstTarget)),
                EntityRayBucket(6.0, listOf(secondTarget)),
                EntityRayBucket(10.0, listOf(thirdTarget)),
            ),
        )
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        Fortune.shoot(player, weaponStack, 1)

        assertTrue(world.consumedEntityBucketCount in 1..2)
        assertEquals(expected = listOf(2.0), actual = world.blockRaycastDistances)
    }

    @Test
    fun rayWithoutEntityHitsSkipsBlockRaycast() {
        AlgorithmResolution.clear()
        val world = TargetWorld(emptyList())
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        Fortune.shoot(player, weaponStack, 1)

        assertEquals(expected = emptyList(), actual = world.blockRaycastDistances)
    }

    @Test
    fun gunCanLetItsRayPassThroughEveryBlock() {
        val target = createTarget(2, targetBox()) { _ -> }
        val world = TargetWorld(listOf(target), Geometry.vector(0.0, 0.0, 4.0))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)
        val gun = BlockPolicyTestGun(true)

        gun.shoot(player, weaponStack, 1)

        assertEquals(expected = listOf(2), actual = gun.hitEntityIds)
        assertEquals(expected = 0, actual = world.blockRaycastCallCount)
        assertEquals(expected = emptyList(), actual = world.blockRaycastDistances)
    }

    @Test
    fun gunCanIgnoreSelectedBlocksAndStopAtTheNextBlockingBlock() {
        val passThroughIdentifier = Identifier.create("test", "pass_through")
        val blockingIdentifier = Identifier.create("test", "blocking")
        val firstTarget = createTarget(2, Geometry.box(-1.0, -1.0, 2.0, 1.0, 1.0, 3.0)) { _ -> }
        val secondTarget = createTarget(3, Geometry.box(-1.0, -1.0, 6.0, 1.0, 1.0, 7.0)) { _ -> }
        val thirdTarget = createTarget(4, Geometry.box(-1.0, -1.0, 10.0, 1.0, 1.0, 11.0)) { _ -> }
        val targets = listOf(firstTarget, secondTarget, thirdTarget)
        val world = TargetWorld(
            targets,
            listOf(
                TestBlockHit(Geometry.vector(0.0, 0.0, 4.0), passThroughIdentifier),
                TestBlockHit(Geometry.vector(0.0, 0.0, 8.0), blockingIdentifier),
            ),
        )
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)
        val gun = BlockPolicyTestGun(false) { blockHit -> blockHit.blockState.block.identifier.asString() != passThroughIdentifier.asString() }

        gun.shoot(player, weaponStack, 1)

        assertEquals(expected = listOf(2, 3), actual = gun.hitEntityIds)
        assertEquals(expected = listOf(2.0, 4.0, 4.0), actual = world.blockRaycastDistances)
    }

    @Test
    fun everyRaycastPolicyTargetsMultipartPartsInsteadOfTheirParent() {
        var parts = emptyArray<EntityPartAccess>()
        val parent = createMultipartTarget(2, Geometry.box(-2.0, -2.0, 1.0, 2.0, 2.0, 8.0), { parts }) { _ -> }
        parts = arrayOf(
            createEntityPart(3, Geometry.box(-1.0, -1.0, 2.0, 1.0, 1.0, 3.0), parent) { _ -> },
            createEntityPart(4, Geometry.box(-1.0, -1.0, 6.0, 1.0, 1.0, 7.0), parent) { _ -> },
        )
        val world = TargetWorld(listOf(parent))
        val ray = Geometry.ray(Geometry.vector(0.0, 0.0, 0.0), Geometry.vector(0.0, 0.0, 1.0))

        for (policy in RaycastExecutionPolicy.entries) {
            val hitEntityIds = world.raycastEntityHits(ray, 20.0, null, policy)
                .map(EntityRayHit::entity)
                .map(EntityAccess::id)
                .toList()

            assertEquals(expected = listOf(3, 4), actual = hitEntityIds, message = policy.name)
        }
    }

    @Test
    fun firearmDamagesMultipartEntityOnlyThroughNearestHitPart() {
        val damagedPartIds = mutableListOf<Int>()
        var parts = emptyArray<EntityPartAccess>()
        val parent = createMultipartTarget(2, Geometry.box(-2.0, -2.0, 1.0, 2.0, 2.0, 8.0), { parts }) { _ -> error("Multipart parent must not receive direct ray damage") }
        parts = arrayOf(
            createEntityPart(3, Geometry.box(-1.0, -1.0, 2.0, 1.0, 1.0, 3.0), parent) { _ -> damagedPartIds += 3 },
            createEntityPart(4, Geometry.box(-1.0, -1.0, 6.0, 1.0, 1.0, 7.0), parent) { _ -> damagedPartIds += 4 },
        )
        val world = TargetWorld(listOf(parent))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        Fortune.shoot(player, weaponStack, 1)

        assertEquals(expected = listOf(3), actual = damagedPartIds)
    }

    @Test
    fun zeusStartsItsChainFromHitPartAndTreatsParentAsVisited() {
        var chainDamagePoints = 0.0
        var parts = emptyArray<EntityPartAccess>()
        val parent = createMultipartTarget(2, Geometry.box(-2.0, -2.0, 1.0, 2.0, 2.0, 8.0), { parts }) { _ -> }
        parts = arrayOf(
            createEntityPart(3, Geometry.box(-0.5, -0.5, 5.0, 0.5, 0.5, 6.0), parent) { _ -> },
        )
        val chainTarget = createTarget(4, Geometry.box(1.25, -0.25, 5.25, 1.75, 0.25, 5.75)) { damagePoints -> chainDamagePoints = damagePoints }
        val world = TargetWorld(listOf(parent, chainTarget))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        Zeus.shoot(player, weaponStack, 1)

        assertEquals(expected = 10_000.0, actual = chainDamagePoints)
    }

    @Test
    fun zeusChainsThroughNearestTargetsRecursively() {
        val receivedDamagePoints = mutableMapOf<Int, Double>()
        val mainTarget = createTarget(2, targetBox()) { damagePoints -> receivedDamagePoints[2] = damagePoints }
        val firstChainTarget = createTarget(3, Geometry.box(1.0, -0.5, 5.0, 2.0, 0.5, 6.0)) { damagePoints -> receivedDamagePoints[3] = damagePoints }
        val secondChainTarget = createTarget(4, Geometry.box(2.5, -0.5, 5.0, 3.5, 0.5, 6.0)) { damagePoints -> receivedDamagePoints[4] = damagePoints }
        val unreachableTarget = createTarget(5, Geometry.box(5.5, -0.5, 5.0, 6.5, 0.5, 6.0)) { damagePoints -> receivedDamagePoints[5] = damagePoints }
        val world = TargetWorld(listOf(mainTarget, firstChainTarget, secondChainTarget, unreachableTarget))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        Zeus.shoot(player, weaponStack, 2)

        assertEquals(expected = 400_000.0, actual = receivedDamagePoints[2])
        assertEquals(expected = 20_000.0, actual = receivedDamagePoints[3])
        assertEquals(expected = 20_000.0, actual = receivedDamagePoints[4])
        assertTrue(5 !in receivedDamagePoints)
    }

    @Test
    fun zeusChoosesNearestTargetInsteadOfDamagingInitialArea() {
        val chainedEntityIds = mutableListOf<Int>()
        val mainTarget = createTarget(2, targetBox()) { _ -> }
        val nearestTarget = createTarget(3, Geometry.box(0.5, -0.5, 5.0, 1.5, 0.5, 6.0)) { _ -> chainedEntityIds += 3 }
        val otherInitialTarget = createTarget(4, Geometry.box(-2.0, -0.5, 5.0, -1.0, 0.5, 6.0)) { _ -> chainedEntityIds += 4 }
        val world = TargetWorld(listOf(mainTarget, nearestTarget, otherInitialTarget))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        Zeus.shoot(player, weaponStack, 1)

        assertEquals(expected = listOf(3), actual = chainedEntityIds)
    }

    @Test
    fun fullCoverageZeusDamagesEveryReachableBranchAndPreservesItsEdges() {
        AlgorithmResolution.clear()
        val transport = Services.PayloadTransport as RecordingPayloadTransport
        val chainedEntityIds = mutableListOf<Int>()
        val mainTargetDamagePoints = mutableListOf<Double>()
        val mainTarget = createTarget(2, targetBox()) { damagePoints -> mainTargetDamagePoints += damagePoints }
        val leftTarget = createTarget(3, chainTargetBox(-1.5)) { _ -> chainedEntityIds += 3 }
        val rightTarget = createTarget(4, chainTargetBox(1.5)) { _ -> chainedEntityIds += 4 }
        val leftBranchTarget = createTarget(5, chainTargetBox(-3.0)) { _ -> chainedEntityIds += 5 }
        val unreachableTarget = createTarget(6, chainTargetBox(5.0)) { _ -> chainedEntityIds += 6 }
        val world = TargetWorld(listOf(mainTarget, leftTarget, rightTarget, leftBranchTarget, unreachableTarget))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createServerPlayer(world, weaponStack)

        try {
            transport.clear()
            FullCoverageZeus.shoot(player, weaponStack, 1)

            assertEquals(expected = listOf(3, 4, 5), actual = chainedEntityIds)
            assertEquals(expected = listOf(200_000.0), actual = mainTargetDamagePoints)
            val payload = transport.trackingPayloads.single() as ZeusChainPayload
            assertEquals(
                expected = listOf(
                    ZeusChainSegment(mainTarget.boundingBox.center, leftTarget.boundingBox.center),
                    ZeusChainSegment(mainTarget.boundingBox.center, rightTarget.boundingBox.center),
                    ZeusChainSegment(leftTarget.boundingBox.center, leftBranchTarget.boundingBox.center),
                ),
                actual = payload.chainSegments,
            )
        } finally {
            transport.clear()
        }
    }

    @Test
    fun zeusChainsToEachEntityIdOnlyOnce() {
        var chainDamageCallCount = 0
        val mainTarget = createTarget(2, targetBox()) { _ -> }
        val nearbyTarget = createTarget(3, Geometry.box(1.0, -0.5, 5.0, 2.0, 0.5, 6.0)) { _ -> chainDamageCallCount++ }
        val world = TargetWorld(listOf(mainTarget, nearbyTarget, nearbyTarget))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        Zeus.shoot(player, weaponStack, 1)

        assertEquals(expected = 1, actual = chainDamageCallCount)
    }

    @Test
    fun zeusDoesNotChainToNonLivingEntitySpawnedByDirectHit() {
        AlgorithmResolution.clear()
        val transport = Services.PayloadTransport as RecordingPayloadTransport
        val targets = mutableListOf<EntityAccess>()
        var spawnedEntityDamagePoints = 0.0
        val spawnedEntity = createTarget(3, Geometry.box(-0.125, -1.0, 5.375, 0.125, -0.75, 5.625), false) { damagePoints -> spawnedEntityDamagePoints = damagePoints }
        val mainTarget = createTarget(2, targetBox()) { _ -> targets += spawnedEntity }
        targets += mainTarget
        val world = TargetWorld(targets)
        val weaponStack = proxy<ItemStackAccess>()
        val player = createServerPlayer(world, weaponStack)

        try {
            transport.clear()
            Zeus.shoot(player, weaponStack, 1)

            assertEquals(expected = 0.0, actual = spawnedEntityDamagePoints)
            assertTrue(transport.trackingPayloads.isEmpty())
        } finally {
            transport.clear()
        }
    }

    @Test
    fun zeusSendsEntireChainInOneTrackingPayload() {
        AlgorithmResolution.clear()
        val transport = Services.PayloadTransport as RecordingPayloadTransport
        val mainTarget = createTarget(2, targetBox()) { _ -> }
        val firstChainTarget = createTarget(3, Geometry.box(1.0, -0.5, 5.0, 2.0, 0.5, 6.0)) { _ -> }
        val secondChainTarget = createTarget(4, Geometry.box(2.5, -0.5, 5.0, 3.5, 0.5, 6.0)) { _ -> }
        val world = TargetWorld(listOf(mainTarget, firstChainTarget, secondChainTarget))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createServerPlayer(world, weaponStack)

        try {
            transport.clear()
            Zeus.shoot(player, weaponStack, 1)

            assertEquals(expected = 1, actual = transport.trackingPayloads.size)
            val payload = transport.trackingPayloads.single() as ZeusChainPayload
            assertEquals(expected = 2, actual = payload.chainSegments.size)
        } finally {
            transport.clear()
        }
    }

    private fun verifyBatchedDamage(firearm: Firearm, shotCount: Long, expectedDamagePoints: Double) {
        AlgorithmResolution.clear()
        var receivedDamagePoints = 0.0
        val target = createTarget(2, targetBox()) { damagePoints -> receivedDamagePoints = damagePoints }
        val world = TargetWorld(listOf(target))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        firearm.shoot(player, weaponStack, shotCount)

        assertEquals(expected = expectedDamagePoints, actual = receivedDamagePoints)
        assertEquals(expected = 1, actual = world.entityRayBucketCallCount)
        assertEquals(expected = 1, actual = world.blockRaycastCallCount)
    }

    private fun verifyRayTraceDistance(firearm: Firearm, expectedDistanceBlocks: Double) {
        AlgorithmResolution.clear()
        val target = createTarget(2, targetBox()) { _ -> }
        val world = TargetWorld(listOf(target))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        firearm.shoot(player, weaponStack, 1)

        assertEquals(expected = expectedDistanceBlocks, actual = world.entityRaycastDistanceBlocks)
    }

    private fun verifyChainDistance(firearm: Firearm, expectedDistanceBlocks: Double) {
        verifyChainTargetDamage(firearm, expectedDistanceBlocks, 10_000.0)
        verifyChainTargetDamage(firearm, expectedDistanceBlocks + 0.01, 0.0)
    }

    private fun verifyChainTargetDamage(firearm: Firearm, targetDistanceBlocks: Double, expectedDamagePoints: Double) {
        AlgorithmResolution.clear()
        var receivedDamagePoints = 0.0
        val mainTarget = createTarget(2, targetBox()) { _ -> }
        val chainTarget = createTarget(3, chainTargetBox(targetDistanceBlocks)) { damagePoints -> receivedDamagePoints = damagePoints }
        val world = TargetWorld(listOf(mainTarget, chainTarget))
        val weaponStack = proxy<ItemStackAccess>()
        val player = createPlayer(world, weaponStack)

        firearm.shoot(player, weaponStack, 1)

        assertEquals(expected = expectedDamagePoints, actual = receivedDamagePoints)
    }

    private fun createPlayer(world: WorldAccess, weaponStack: ItemStackAccess, eyePosition: VectorView = Geometry.vector(0.0, 0.0, 0.0), recordExperience: (Int) -> Unit = {}): PlayerAccess {
        return createPlayerProxy(PlayerAccess::class.java, world, weaponStack, recordExperience, eyePosition)
    }

    private fun createServerPlayer(world: WorldAccess, weaponStack: ItemStackAccess, eyePosition: VectorView = Geometry.vector(0.0, 0.0, 0.0), recordExperience: (Int) -> Unit = {}): ServerPlayerAccess {
        return createPlayerProxy(ServerPlayerAccess::class.java, world, weaponStack, recordExperience, eyePosition)
    }

    private fun <Player : PlayerAccess> createPlayerProxy(playerType: Class<Player>, world: WorldAccess, weaponStack: ItemStackAccess, recordExperience: (Int) -> Unit, eyePosition: VectorView): Player {
        val player = Proxy.newProxyInstance(
            playerType.classLoader,
            arrayOf(playerType, EntityEquipmentAccess::class.java),
        ) { player, method, arguments ->
            when (method.name) {
                "getId" -> 1
                "getWorld" -> world
                "getPosition" -> Geometry.vector(0.0, 0.0, 0.0)
                "getEyePosition" -> eyePosition
                "getViewVector" -> Geometry.vector(0.0, 0.0, 1.0)
                "getEquippedStack" -> weaponStack
                "addExperiencePoints" -> recordExperience(arguments?.get(0) as Int)
                "hashCode" -> System.identityHashCode(player)
                "equals" -> player === arguments?.firstOrNull()
                else -> error("Unsupported PlayerAccess method: " + method.name)
            }
        }
        return playerType.cast(player)
    }

    private fun createTarget(id: Int, boundingBox: BoxView, isLivingEntity: Boolean = true, maximumHealth: Double = DEFAULT_TARGET_HEALTH, applyDamageToHealth: Boolean = true, recordExecution: (DamageSourceView) -> Unit = {}, recordDeferredExperienceReceiver: (PlayerAccess) -> Unit = {}, recordDamage: (Double) -> Unit): EntityAccess {
        val entityType = if (isLivingEntity) LivingEntityAccess::class.java else EntityAccess::class.java
        val interfaces = if (isLivingEntity) {
            arrayOf(
                entityType,
                EntityExecutionAccess::class.java,
                DeferredExperienceDropAccess::class.java,
            )
        } else {
            arrayOf(entityType)
        }
        var health = maximumHealth
        return Proxy.newProxyInstance(entityType.classLoader, interfaces) { target, method, arguments ->
            when (method.name) {
                "getId" -> id
                "getPosition" -> Geometry.vector(boundingBox.center.x, boundingBox.minY, boundingBox.center.z)
                "getBoundingBox" -> boundingBox
                "getHealth" -> health
                "setHealth" -> {
                    health = (arguments?.get(0) as Double).coerceIn(0.0, maximumHealth)
                }

                "getMaximumHealth" -> maximumHealth
                "isAlive" -> !isLivingEntity || health > 0.0
                "isRemoved" -> false
                "hurt" -> {
                    val damagePoints = arguments?.get(1) as Double
                    recordDamage(damagePoints)
                    if (isLivingEntity && applyDamageToHealth) {
                        health = (health - damagePoints).coerceAtLeast(0.0)
                    }
                    true
                }

                "execute" -> {
                    recordExecution(arguments?.get(0) as DamageSourceView)
                    health = 0.0
                }

                "sendDeferredExperienceTo" -> {
                    recordDeferredExperienceReceiver(arguments?.get(0) as PlayerAccess)
                }

                "hashCode" -> System.identityHashCode(target)
                "equals" -> target === arguments?.firstOrNull()
                else -> error("Unsupported EntityAccess method: " + method.name)
            }
        } as EntityAccess
    }

    private fun createMultipartTarget(id: Int, boundingBox: BoxView, partsProvider: () -> Array<out EntityPartAccess>, recordDamage: (Double) -> Unit): EntityAccess {
        var health = DEFAULT_TARGET_HEALTH
        return Proxy.newProxyInstance(
            MultipartEntityAccess::class.java.classLoader,
            arrayOf(
                LivingEntityAccess::class.java,
                MultipartEntityAccess::class.java,
                EntityExecutionAccess::class.java,
                DeferredExperienceDropAccess::class.java,
            ),
        ) { target, method, arguments ->
            when (method.name) {
                "getId" -> id
                "getPosition" -> Geometry.vector(boundingBox.center.x, boundingBox.minY, boundingBox.center.z)
                "getBoundingBox" -> boundingBox
                "getHealth" -> health
                "setHealth" -> {
                    health = (arguments?.get(0) as Double).coerceIn(0.0, DEFAULT_TARGET_HEALTH)
                }

                "getMaximumHealth" -> DEFAULT_TARGET_HEALTH
                "getParts" -> partsProvider()
                "isAlive" -> health > 0.0
                "isRemoved" -> false
                "hurt" -> {
                    val damagePoints = arguments?.get(1) as Double
                    recordDamage(damagePoints)
                    health = (health - damagePoints).coerceAtLeast(0.0)
                    true
                }

                "execute" -> health = 0.0
                "sendDeferredExperienceTo" -> Unit

                "hashCode" -> System.identityHashCode(target)
                "equals" -> target === arguments?.firstOrNull()
                else -> error("Unsupported multipart entity method: " + method.name)
            }
        } as EntityAccess
    }

    private fun createEntityPart(id: Int, boundingBox: BoxView, parent: EntityAccess, recordDamage: (Double) -> Unit): EntityPartAccess {
        return Proxy.newProxyInstance(
            EntityPartAccess::class.java.classLoader,
            arrayOf(EntityPartAccess::class.java),
        ) { part, method, arguments ->
            when (method.name) {
                "getId" -> id
                "getBoundingBox" -> boundingBox
                "getParent" -> parent
                "hurt" -> {
                    val damagePoints = arguments?.get(1) as Double
                    recordDamage(damagePoints)
                    val livingParent = parent as? LivingEntityAccess
                    if (livingParent != null) {
                        livingParent.health = (livingParent.health - damagePoints).coerceAtLeast(0.0)
                    }
                    true
                }

                "hashCode" -> System.identityHashCode(part)
                "equals" -> part === arguments?.firstOrNull()
                else -> error("Unsupported entity part method: " + method.name)
            }
        } as EntityPartAccess
    }

    private fun createExperienceOrb(id: Int, experiencePoints: Int, remove: () -> Unit): EntityAccess {
        return Proxy.newProxyInstance(
            ExperienceOrbAccess::class.java.classLoader,
            arrayOf(ExperienceOrbAccess::class.java),
        ) { orb, method, arguments ->
            when (method.name) {
                "getId" -> id
                "getExperiencePoints" -> experiencePoints
                "remove" -> remove()
                "hashCode" -> System.identityHashCode(orb)
                "equals" -> orb === arguments?.firstOrNull()
                else -> error("Unsupported experience orb method: " + method.name)
            }
        } as EntityAccess
    }

    private fun createDroppedItem(id: Int, makeImmediatelyCollectible: () -> Unit, setPosition: (VectorView) -> Unit): EntityAccess {
        return Proxy.newProxyInstance(
            DroppedItemAccess::class.java.classLoader,
            arrayOf(DroppedItemAccess::class.java),
        ) { item, method, arguments ->
            when (method.name) {
                "getId" -> id
                "makeImmediatelyCollectible" -> makeImmediatelyCollectible()
                "setPosition" -> setPosition(arguments?.get(0) as VectorView)
                "hashCode" -> System.identityHashCode(item)
                "equals" -> item === arguments?.firstOrNull()
                else -> error("Unsupported dropped item method: " + method.name)
            }
        } as EntityAccess
    }

    private fun targetBox(): BoxView {
        return Geometry.box(-1.0, -1.0, 5.0, 1.0, 1.0, 6.0)
    }

    private fun chainTargetBox(distanceBlocks: Double): BoxView {
        return Geometry.box(distanceBlocks - 0.25, -0.25, 5.25, distanceBlocks + 0.25, 0.25, 5.75)
    }

    private inline fun <reified Access : Any> proxy(): Access {
        return Proxy.newProxyInstance(Access::class.java.classLoader, arrayOf(Access::class.java)) { _, method, _ ->
            error("Unsupported " + Access::class.java.simpleName + " method: " + method.name)
        } as Access
    }

    private class BlockPolicyTestGun(private val canPassThroughEveryBlock: Boolean = false, private val blocksRay: (BlockHitResult) -> Boolean = { true }) : RayTraceGun() {
        override val identifier = Identifier.create("test", "block_policy_gun")
        val hitEntityIds = mutableListOf<Int>()

        override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
            return Frequency.perSecond(1)
        }

        override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
            return 20.0
        }

        override fun canRayPassThroughAllBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean {
            return canPassThroughEveryBlock
        }

        override fun isRayBlockedBy(player: PlayerAccess, weaponStack: ItemStackAccess, blockHit: BlockHitResult): Boolean {
            return blocksRay(blockHit)
        }

        override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
            hitEntityIds += entityHits
                .map(EntityRayHit::entity)
                .map(EntityAccess::id)
                .toList()
        }
    }

    private data class TestBlock(
        override val identifier: Identifier,
    ) : BlockAccess

    private data class TestBlockState(
        override val block: BlockAccess,
    ) : BlockStateAccess

    private class PersistentTestItemStack : ItemStackAccess, PersistentDataAccess {
        private val longValues = mutableMapOf<Identifier, Long>()
        private val doubleValues = mutableMapOf<Identifier, Double>()

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

        override fun getDouble(key: Identifier): Double? = doubleValues[key]

        override fun setDouble(key: Identifier, value: Double) {
            doubleValues[key] = value
        }

        override fun remove(key: Identifier) {
            longValues.remove(key)
            doubleValues.remove(key)
        }
    }

    private data class TestBlockHit(
        val point: VectorView,
        val blockIdentifier: Identifier,
    )

    private class TargetWorld(private val targets: List<EntityAccess>, blockHitPoint: VectorView? = null, private val entityBuckets: List<EntityRayBucket>? = null, private val additionalEntities: () -> Sequence<EntityAccess> = ::emptySequence, private val blockHits: List<TestBlockHit> = defaultBlockHits(blockHitPoint)) : WorldAccess {
        constructor(targets: List<EntityAccess>, additionalEntities: () -> Sequence<EntityAccess>) : this(targets, null, null, additionalEntities)

        constructor(targets: List<EntityAccess>, blockHits: List<TestBlockHit>) : this(targets, null, null, ::emptySequence, blockHits)

        var entityRayBucketCallCount = 0
            private set
        var consumedEntityBucketCount = 0
            private set
        var blockRaycastCallCount = 0
            private set
        var entityRaycastDistanceBlocks = 0.0
            private set
        val blockRaycastDistances = mutableListOf<Double>()
        val blockRaycastShapes = mutableListOf<BlockRaycastShape>()

        override val isClientSide = false
        override val loadedEntityCount = targets.size
        override val entities: Sequence<EntityAccess>
            get() = targets.asSequence()

        override fun getEntities(searchBox: BoxView): Sequence<EntityAccess> {
            return sequence {
                yieldAll(targets)
                yieldAll(additionalEntities())
            }
                .distinctBy(EntityAccess::id)
        }

        override fun getEntityRayBuckets(ray: RayView, length: Double): Sequence<EntityRayBucket> {
            entityRayBucketCallCount++
            entityRaycastDistanceBlocks = length
            val buckets = entityBuckets ?: listOf(EntityRayBucket(0.0, targets))
            return sequence {
                for (bucket in buckets) {
                    consumedEntityBucketCount++
                    yield(bucket)
                }
            }
        }

        override fun raycastBlockHits(ray: RayView, distanceBlocks: Double, shape: BlockRaycastShape): Sequence<BlockHitResult> {
            blockRaycastCallCount++
            blockRaycastDistances += distanceBlocks
            blockRaycastShapes += shape
            val directionLengthSquared = ray.direction.lengthSquared
            if (directionLengthSquared == 0.0) return emptySequence()

            return blockHits
                .asSequence()
                .mapNotNull { blockHit ->
                    val offsetX = blockHit.point.x - ray.origin.x
                    val offsetY = blockHit.point.y - ray.origin.y
                    val offsetZ = blockHit.point.z - ray.origin.z
                    val time = (offsetX * ray.direction.x + offsetY * ray.direction.y + offsetZ * ray.direction.z) / directionLengthSquared
                    val hitDistanceBlocks = time * ray.direction.length
                    if (time < 0.0 || hitDistanceBlocks > distanceBlocks) return@mapNotNull null

                    BlockHitResult(BlockPositions.Zero, TestBlockState(TestBlock(blockHit.blockIdentifier)), BlockDirection.North, blockHit.point, time)
                }
                .sortedBy(BlockHitResult::time)
        }

        override fun destroyBlock(position: BlockPositionView, dropItems: Boolean): Boolean {
            return false
        }

        override fun playSound(position: VectorView, playback: SoundPlayback) {
        }

        override fun spawnParticles(position: VectorView, effect: ParticleEffect) {
        }
    }

    private companion object {
        const val DEFAULT_TARGET_HEALTH = 1_000_000_000.0

        val FullCoverageZeus = object : Zeus("full_coverage_zeus", 200_000.0, 70.0, 2.0, ZeusChainMode.FullCoverage) {}

        fun defaultBlockHits(blockHitPoint: VectorView?): List<TestBlockHit> = blockHitPoint?.let { point -> listOf(TestBlockHit(point, Identifier.create("minecraft", "stone"))) }.orEmpty()
    }
}
