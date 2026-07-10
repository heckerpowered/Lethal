/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.adapter.world.raycast

/**
 * Input features used to choose an entity raycast algorithm.
 */
data class AlgorithmResolutionState(val loadedEntityCount: Int, val distanceBlocks: Double)

/**
 * Online selector for entity raycast algorithms.
 *
 * Each coarse input bucket probes every algorithm once, then follows the highest score.
 */
object AlgorithmResolution {
    private const val RECENT_SAMPLE_WEIGHT = 0.125
    private const val OBSERVATION_CONFIDENCE_SAMPLE_COUNT = 2.0
    private const val SHORT_DISTANCE_BLOCKS = 64.0
    private const val LONG_DISTANCE_BLOCKS = 128.0
    private const val VERY_LONG_DISTANCE_BLOCKS = 256.0
    private const val EXTREME_DISTANCE_BLOCKS = 512.0
    private const val LOW_ENTITY_COUNT = 128
    private const val MODERATE_ENTITY_COUNT = 256
    private const val WIDE_ENTITY_COUNT = 384

    private val records = mutableMapOf<AlgorithmResolutionKey, AlgorithmResolutionRecord>()

    @Synchronized
    fun resolve(state: AlgorithmResolutionState): RaycastExecutionPolicy {
        return recordFor(state).resolve(state)
    }

    @Synchronized
    fun record(state: AlgorithmResolutionState, policy: RaycastExecutionPolicy, elapsedNanoseconds: Long) {
        recordFor(state).record(policy, elapsedNanoseconds)
    }

    @Synchronized
    fun clear() {
        records.clear()
    }

    private fun recordFor(state: AlgorithmResolutionState): AlgorithmResolutionRecord {
        return records.getOrPut(AlgorithmResolutionKey(state)) { AlgorithmResolutionRecord() }
    }

    private data class AlgorithmResolutionKey(val loadedEntityCountBucket: Int, val distanceBucket: Int) {
        constructor(state: AlgorithmResolutionState) : this(bucket(state.loadedEntityCount), bucket(state.distanceBlocks))
    }

    private class AlgorithmResolutionRecord {
        private val estimates = RaycastExecutionPolicy.entries.associateWith { AlgorithmEstimate() }

        fun resolve(state: AlgorithmResolutionState): RaycastExecutionPolicy {
            return unmeasuredPolicy(state) ?: RaycastExecutionPolicy.entries.maxBy { score(it, state) }
        }

        fun record(policy: RaycastExecutionPolicy, elapsedNanoseconds: Long) {
            estimates[policy]?.record(elapsedNanoseconds)
        }

        private fun unmeasuredPolicy(state: AlgorithmResolutionState): RaycastExecutionPolicy? {
            var selectedPolicy: RaycastExecutionPolicy? = null
            var selectedScore = Double.NEGATIVE_INFINITY

            for (policy in RaycastExecutionPolicy.entries) {
                if (estimates.getValue(policy).sampleCount != 0) continue

                val score = preferenceCoefficient(policy, state)
                if (score <= selectedScore) continue

                selectedPolicy = policy
                selectedScore = score
            }

            return selectedPolicy
        }

        private fun score(policy: RaycastExecutionPolicy, state: AlgorithmResolutionState): Double {
            return preferenceCoefficient(policy, state) * measurementFitness(policy)
        }

        private fun measurementFitness(policy: RaycastExecutionPolicy): Double {
            val estimate = estimates.getValue(policy)
            if (estimate.sampleCount == 0) return 1.0

            val bestAverageElapsedNanoseconds = estimates.values
                .filter { it.sampleCount > 0 }
                .minOf { it.averageElapsedNanoseconds }

            val relativeFitness = bestAverageElapsedNanoseconds / estimate.averageElapsedNanoseconds
            val confidence = estimate.sampleCount.toDouble() / (estimate.sampleCount.toDouble() + OBSERVATION_CONFIDENCE_SAMPLE_COUNT)
            return 1.0 - confidence + relativeFitness * confidence
        }
    }

    private class AlgorithmEstimate {
        var averageElapsedNanoseconds = 0.0
            private set

        var sampleCount = 0
            private set

        fun record(elapsedNanoseconds: Long) {
            val elapsed = elapsedNanoseconds.coerceAtLeast(1L).toDouble()
            averageElapsedNanoseconds = if (sampleCount == 0) elapsed else averageElapsedNanoseconds * (1.0 - RECENT_SAMPLE_WEIGHT) + elapsed * RECENT_SAMPLE_WEIGHT
            sampleCount++
        }
    }

    private fun preferenceCoefficient(policy: RaycastExecutionPolicy, state: AlgorithmResolutionState): Double {
        return when (policy) {
            RaycastExecutionPolicy.BROAD_PHASE -> broadPhasePreferenceCoefficient(state)
            RaycastExecutionPolicy.ORDERED_BUCKET -> orderedBucketPreferenceCoefficient(state)
            RaycastExecutionPolicy.FULL_SCAN -> fullScanPreferenceCoefficient(state)
        }
    }

    private fun broadPhasePreferenceCoefficient(state: AlgorithmResolutionState): Double {
        if (state.distanceBlocks < SHORT_DISTANCE_BLOCKS) return 1.25
        if (state.distanceBlocks < LONG_DISTANCE_BLOCKS) return 0.95

        return 0.65
    }

    private fun orderedBucketPreferenceCoefficient(state: AlgorithmResolutionState): Double {
        if (state.distanceBlocks < SHORT_DISTANCE_BLOCKS) return 0.85
        if (state.distanceBlocks < VERY_LONG_DISTANCE_BLOCKS) return 1.04
        if (state.distanceBlocks < EXTREME_DISTANCE_BLOCKS) return 0.95

        return 0.70
    }

    private fun fullScanPreferenceCoefficient(state: AlgorithmResolutionState): Double {
        if (state.distanceBlocks >= EXTREME_DISTANCE_BLOCKS) return 1.35
        if (state.loadedEntityCount <= LOW_ENTITY_COUNT && state.distanceBlocks >= VERY_LONG_DISTANCE_BLOCKS) return 1.20
        if (state.loadedEntityCount <= MODERATE_ENTITY_COUNT && state.distanceBlocks >= LONG_DISTANCE_BLOCKS) return 1.14
        if (state.loadedEntityCount <= WIDE_ENTITY_COUNT && state.distanceBlocks >= LONG_DISTANCE_BLOCKS) return 1.08
        if (state.distanceBlocks >= VERY_LONG_DISTANCE_BLOCKS) return 1.12

        return 1.0
    }

    private fun bucket(value: Int): Int {
        if (value <= 0) return 0

        var bucket = 0
        var upperBound = 1

        while (upperBound < value && upperBound < Int.MAX_VALUE / 2) {
            upperBound *= 2
            bucket++
        }

        return bucket
    }

    private fun bucket(value: Double): Int {
        if (value <= 0.0) return 0

        var bucket = 0
        var upperBound = 1.0

        while (upperBound < value && upperBound < Double.MAX_VALUE / 2.0) {
            upperBound *= 2.0
            bucket++
        }

        return bucket
    }
}
