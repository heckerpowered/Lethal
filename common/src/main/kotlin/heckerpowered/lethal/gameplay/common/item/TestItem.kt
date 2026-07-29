/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.item.*
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.AlgorithmResolution
import heckerpowered.bridge.adapter.world.raycast.AlgorithmResolutionState
import heckerpowered.bridge.adapter.world.raycast.RaycastExecutionPolicy
import heckerpowered.bridge.adapter.world.raycast.raycastEntityHits
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.Constants

object TestItem : ItemBlueprint {
    override val properties: ItemProperties = ItemProperties(1, 0, null)
    override val identifier: Identifier = Constants.identifier("test_item")
    override val form: ItemForm = ItemForm.Regular

    override fun use(stack: ItemStackAccess, world: WorldAccess, user: EntityAccess, hand: Hand): ItemInteractionResult {
        if (world.isClientSide) return super.use(stack, world, user, hand)

        AlgorithmResolution.clear()

        val iterations = 1000

        val distances = generateSequence(2.0) { it * 2.0 }
            .takeWhile { it <= 1024.0 }
            .toList()

        val policies = RaycastExecutionPolicy.entries.toList()

        var sink = 1
        var decisionCount = 0
        var priorCorrectCount = 0
        var learnedCorrectCount = 0
        var priorLossPercentTotal = 0.0
        var learnedLossPercentTotal = 0.0
        var priorWorstLossPercent = 0.0
        var learnedWorstLossPercent = 0.0

        println("Raycast policy benchmark")
        println("iterations=$iterations, entities=${world.loadedEntityCount}")
        println("distance, policy, totalMicroseconds, nanosecondsPerOperation, hits, sink")

        for (distance in distances) {
            val ray = Geometry.ray(user.eyePosition, user.viewVector)
            val resolutionState = AlgorithmResolutionState(world.loadedEntityCount, distance)
            val priorPolicy = AlgorithmResolution.resolve(resolutionState)
            val measurements = mutableMapOf<RaycastExecutionPolicy, RaycastPolicyMeasurement>()

            for (policy in policies) {
                var hits = 0

                val elapsedNanoseconds = kotlin.system.measureNanoTime {
                    repeat(iterations) {
                        val hit = world.raycastEntityHits(ray, distance, user, policy)
                            .firstOrNull()

                        if (hit != null) {
                            hits++
                            sink = sink * 31 + hit.entity.hashCode()
                        } else {
                            sink *= 31
                        }
                    }
                }

                val totalMicroseconds = elapsedNanoseconds / 1_000L
                val nanosecondsPerOperation = elapsedNanoseconds / iterations
                measurements[policy] = RaycastPolicyMeasurement(nanosecondsPerOperation)
                AlgorithmResolution.record(resolutionState, policy, nanosecondsPerOperation)

                println("$distance, ${policy.name}, ${totalMicroseconds}us, ${nanosecondsPerOperation}ns/op, hits=$hits, sink=$sink")
            }

            val bestMeasurement = measurements.entries.minBy { it.value.nanosecondsPerOperation }
            val bestPolicy = bestMeasurement.key
            val bestNanosecondsPerOperation = bestMeasurement.value.nanosecondsPerOperation
            val learnedPolicy = AlgorithmResolution.resolve(resolutionState)
            val priorLossPercent = (measurements.getValue(priorPolicy).nanosecondsPerOperation - bestNanosecondsPerOperation) * 100.0 / bestNanosecondsPerOperation
            val learnedLossPercent = (measurements.getValue(learnedPolicy).nanosecondsPerOperation - bestNanosecondsPerOperation) * 100.0 / bestNanosecondsPerOperation

            decisionCount++
            if (priorPolicy == bestPolicy) priorCorrectCount++
            if (learnedPolicy == bestPolicy) learnedCorrectCount++

            priorLossPercentTotal += priorLossPercent
            learnedLossPercentTotal += learnedLossPercent
            if (priorLossPercent > priorWorstLossPercent) priorWorstLossPercent = priorLossPercent
            if (learnedLossPercent > learnedWorstLossPercent) learnedWorstLossPercent = learnedLossPercent

            var autoHits = 0
            val autoElapsedNanoseconds = kotlin.system.measureNanoTime {
                repeat(iterations) {
                    val hit = world.raycastEntityHits(ray, distance, user, learnedPolicy)
                        .firstOrNull()

                    if (hit != null) {
                        autoHits++
                        sink = sink * 31 + hit.entity.hashCode()
                    } else {
                        sink *= 31
                    }
                }
            }

            val autoTotalMicroseconds = autoElapsedNanoseconds / 1_000L
            val autoNanosecondsPerOperation = autoElapsedNanoseconds / iterations
            AlgorithmResolution.record(resolutionState, learnedPolicy, autoNanosecondsPerOperation)

            println("$distance, AUTO:${learnedPolicy.name}, ${autoTotalMicroseconds}us, ${autoNanosecondsPerOperation}ns/op, hits=$autoHits, sink=$sink")
        }

        println("priorResolutionAccuracy=$priorCorrectCount/$decisionCount, averageLossPercent=${priorLossPercentTotal / decisionCount}, worstLossPercent=$priorWorstLossPercent")
        println("learnedResolutionAccuracy=$learnedCorrectCount/$decisionCount, averageLossPercent=${learnedLossPercentTotal / decisionCount}, worstLossPercent=$learnedWorstLossPercent")

        return super.use(stack, world, user, hand)
    }
}

private data class RaycastPolicyMeasurement(val nanosecondsPerOperation: Long)
