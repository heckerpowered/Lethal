/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.world.raycast

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityPartAccess
import heckerpowered.bridge.adapter.entity.MultipartEntityAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RayIntersectionContext
import heckerpowered.bridge.math.RayView
import heckerpowered.bridge.math.intersect
import java.util.*
import kotlin.math.max
import kotlin.math.min

fun EntityRayBucketSource.raycastEntityHitsOrdered(ray: RayView, length: Double, excluded: EntityAccess? = null): Sequence<EntityRayHit> {
    val context = RayIntersectionContext.create(ray, length) ?: return emptySequence()
    return getEntityRayBuckets(ray, length).raycastEntityHitsOrdered(context, excluded)
}

fun Sequence<EntityRayBucket>.raycastEntityHitsOrdered(ray: RayView, length: Double, excluded: EntityAccess? = null): Sequence<EntityRayHit> {
    val context = RayIntersectionContext.create(ray, length) ?: return emptySequence()
    return raycastEntityHitsOrdered(context, excluded)
}

fun Sequence<EntityRayBucket>.raycastEntityHitsOrdered(context: RayIntersectionContext, excluded: EntityAccess? = null): Sequence<EntityRayHit> = sequence {
    val iterator = iterator()
    var bucket = iterator.nextOrNull()
    val hits = PriorityQueue(compareBy(EntityRayHit::time))
    val visitedEntityIds = HashSet<Int>()
    var previousLowerBound = Double.NEGATIVE_INFINITY

    while (bucket != null) {
        require(bucket.lowerBoundTime >= previousLowerBound) { "Entity ray buckets must be ordered by lowerBoundTime" }
        previousLowerBound = bucket.lowerBoundTime
        bucket.addRaycastHitsTo(hits, context, excluded, visitedEntityIds)

        val nextBucket = iterator.nextOrNull()
        val futureLowerBound = nextBucket?.lowerBoundTime ?: Double.POSITIVE_INFINITY

        while (hits.isNotEmpty() && hits.peek().time <= futureLowerBound) {
            yield(hits.remove())
        }

        bucket = nextBucket
    }

    while (hits.isNotEmpty()) {
        yield(hits.remove())
    }
}

private fun EntityRayBucket.addRaycastHitsTo(hits: MutableCollection<EntityRayHit>, context: RayIntersectionContext, excluded: EntityAccess?, visitedEntityIds: MutableSet<Int>) {
    for (candidate in entities) {
        candidate.forEachRaycastTarget { entity ->
            if (!visitedEntityIds.add(entity.id) || entity.isExcluded(excluded)) return@forEachRaycastTarget

            val intersection = entity.boundingBox.intersect(context) ?: return@forEachRaycastTarget
            hits += EntityRayHit(entity, intersection)
        }
    }
}

private fun <Element> Iterator<Element>.nextOrNull(): Element? {
    return if (hasNext()) next() else null
}

private fun Sequence<EntityAccess>.asEntityRayHits(context: RayIntersectionContext, excluded: EntityAccess?): Sequence<EntityRayHit> {
    return sequence {
        val visitedEntityIds = HashSet<Int>()
        for (candidate in this@asEntityRayHits) {
            candidate.forEachRaycastTarget { entity ->
                if (!visitedEntityIds.add(entity.id) || entity.isExcluded(excluded)) return@forEachRaycastTarget

                val intersection = entity.boundingBox.intersect(context) ?: return@forEachRaycastTarget
                yield(EntityRayHit(entity, intersection))
            }
        }
    }.sortedBy(EntityRayHit::time)
}

private inline fun EntityAccess.forEachRaycastTarget(action: (EntityAccess) -> Unit) {
    val parts = (this as? MultipartEntityAccess)?.parts
    if (parts.isNullOrEmpty()) {
        action(this)
        return
    }

    for (part in parts) {
        action(part)
    }
}

private fun EntityAccess.isExcluded(excluded: EntityAccess?): Boolean {
    if (excluded == null) return false
    if (id == excluded.id) return true

    return (this as? EntityPartAccess)?.parent?.id == excluded.id
}

fun WorldAccess.raycastEntityHits(ray: RayView, distance: Double, excluded: EntityAccess? = null): Sequence<EntityRayHit> {
    return raycastEntityHitsResolved(ray, distance, excluded)
}

fun WorldAccess.raycastEntityHits(ray: RayView, distance: Double, policy: RaycastExecutionPolicy): Sequence<EntityRayHit> {
    return raycastEntityHits(ray, distance, null, policy)
}

fun WorldAccess.raycastEntityHits(ray: RayView, distance: Double, excluded: EntityAccess?, policy: RaycastExecutionPolicy): Sequence<EntityRayHit> {
    val context = RayIntersectionContext.create(ray, distance) ?: return emptySequence()
    return when (policy) {
        RaycastExecutionPolicy.FULL_SCAN -> entities
            .asEntityRayHits(context, excluded)

        RaycastExecutionPolicy.BROAD_PHASE -> {
            val endX = context.originX + context.directionX * context.maximumTime
            val endY = context.originY + context.directionY * context.maximumTime
            val endZ = context.originZ + context.directionZ * context.maximumTime
            val minimum = Geometry.vector(min(context.originX, endX), min(context.originY, endY), min(context.originZ, endZ))
            val maximum = Geometry.vector(max(context.originX, endX), max(context.originY, endY), max(context.originZ, endZ))
            val searchBox = Geometry.box(minimum, maximum)

            getEntities(searchBox)
                .asEntityRayHits(context, excluded)
        }

        RaycastExecutionPolicy.ORDERED_BUCKET -> getEntityRayBuckets(ray, distance).raycastEntityHitsOrdered(context, excluded)
    }
}

fun WorldAccess.resolveRaycastExecutionPolicy(distance: Double): RaycastExecutionPolicy {
    return AlgorithmResolution.resolve(AlgorithmResolutionState(loadedEntityCount, distance))
}

private fun WorldAccess.raycastEntityHitsResolved(ray: RayView, distance: Double, excluded: EntityAccess?): Sequence<EntityRayHit> {
    val state = AlgorithmResolutionState(loadedEntityCount, distance)
    val policy = AlgorithmResolution.resolve(state)
    return raycastEntityHits(ray, distance, excluded, policy).recordResolution(state, policy)
}

private fun <Element> Sequence<Element>.recordResolution(state: AlgorithmResolutionState, policy: RaycastExecutionPolicy): Sequence<Element> {
    return Sequence {
        val startedAtNanoseconds = System.nanoTime()
        AlgorithmResolutionIterator(iterator(), state, policy, startedAtNanoseconds)
    }
}

private class AlgorithmResolutionIterator<Element>(private val iterator: Iterator<Element>, private val state: AlgorithmResolutionState, private val policy: RaycastExecutionPolicy, private val startedAtNanoseconds: Long) : Iterator<Element> {
    private var recorded = false

    override fun hasNext(): Boolean {
        val hasNext = iterator.hasNext()
        if (!hasNext) record()

        return hasNext
    }

    override fun next(): Element {
        val value = iterator.next()
        record()
        return value
    }

    private fun record() {
        if (recorded) return

        recorded = true
        AlgorithmResolution.record(state, policy, System.nanoTime() - startedAtNanoseconds)
    }
}
