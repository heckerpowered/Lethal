/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.adapter.world.raycast

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.adapter.world.WorldAccess
import heckerpowered.lethal.bridge.math.Geometry
import heckerpowered.lethal.bridge.math.RayView
import heckerpowered.lethal.bridge.math.intersect
import java.util.*
import kotlin.math.max
import kotlin.math.min

fun EntityRayBucketSource.raycastEntityHitsOrdered(ray: RayView, length: Double, excluded: EntityAccess? = null): Sequence<EntityRayHit> {
    return getEntityRayBuckets(ray, length).raycastEntityHitsOrdered(ray, length, excluded)
}

fun Sequence<EntityRayBucket>.raycastEntityHitsOrdered(ray: RayView, length: Double, excluded: EntityAccess? = null): Sequence<EntityRayHit> = sequence {
    val iterator = iterator()
    var bucket = iterator.nextOrNull()
    val hits = PriorityQueue(compareBy<EntityRayHit> { it.time })
    var previousLowerBound = Double.NEGATIVE_INFINITY

    while (bucket != null) {
        require(bucket.lowerBoundTime >= previousLowerBound) {
            "Entity ray buckets must be ordered by lowerBoundTime"
        }
        previousLowerBound = bucket.lowerBoundTime

        for (entity in bucket.entities) {
            if (entity == excluded) continue

            val intersection = entity.boundingBox.intersect(ray, length) ?: continue
            hits += EntityRayHit(entity, intersection)
        }

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

private fun <Element> Iterator<Element>.nextOrNull(): Element? {
    return if (hasNext()) next() else null
}

private fun Sequence<EntityAccess>.asEntityRayHits(ray: RayView, distance: Double, excluded: EntityAccess?): Sequence<EntityRayHit> {
    return mapNotNull { entity ->
        if (entity == excluded) return@mapNotNull null

        val intersection = entity.boundingBox.intersect(ray, distance)
            ?: return@mapNotNull null

        EntityRayHit(entity, intersection)
    }.sortedBy {
        it.time
    }
}

fun WorldAccess.raycastEntityHits(ray: RayView, distance: Double, excluded: EntityAccess? = null, policy: RaycastExecutionPolicy = RaycastExecutionPolicy.AUTO): Sequence<EntityRayHit> {
    return when (policy) {
        RaycastExecutionPolicy.FULL_SCAN -> entities
            .asEntityRayHits(ray, distance, excluded)

        RaycastExecutionPolicy.BROAD_PHASE -> {
            val direction = ray.direction
            if (direction.isNearlyZero()) return emptySequence()

            val end = ray.pointAt(distance / direction.length)
            val origin = ray.origin
            val searchBox = Geometry.box(min(origin.x, end.x), min(origin.y, end.y), min(origin.z, end.z), max(origin.x, end.x), max(origin.y, end.y), max(origin.z, end.z))

            getEntities(searchBox)
                .asEntityRayHits(ray, distance, excluded)
        }

        RaycastExecutionPolicy.ORDERED_BUCKET -> raycastEntityHitsOrdered(ray, distance, excluded)
        RaycastExecutionPolicy.AUTO -> raycastEntityHitsResolved(ray, distance, excluded)
    }
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
