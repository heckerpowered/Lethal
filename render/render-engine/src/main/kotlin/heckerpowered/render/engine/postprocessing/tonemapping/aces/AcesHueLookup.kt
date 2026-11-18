/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing.tonemapping.aces

/**
 * Immutable lookup data shared by chroma and gamut compression. Cusp samples follow the nonuniform
 * hue grid; reach samples follow uniform one-degree steps. Their indices are not interchangeable.
 * Each grid includes three wrapping entries. Search bounds accelerate the nonuniform-grid lookup.
 *
 * All arrays, including every cusp row, are copied after generation finishes. Copies of enclosing
 * data values can safely share this snapshot without changing equality or hash codes.
 */
internal class AcesHueLookup(hues: FloatArray, cusp: Array<FloatArray>, reach: FloatArray, hueSearchRange: IntArray) {
    private val hues = hues.copyOf()
    private val cusp = Array(cusp.size) { cusp[it].copyOf() }
    private val reach = reach.copyOf()
    private val hueSearchRange = hueSearchRange.copyOf()

    val size: Int get() = hues.size
    val searchLow: Int get() = hueSearchRange[0]
    val searchHigh: Int get() = hueSearchRange[1]

    fun hue(index: Int): Float = hues[index]
    fun cusp(index: Int, component: Int): Float = cusp[index][component]
    fun reach(index: Int): Float = reach[index]

    override fun equals(other: Any?): Boolean = this === other || other is AcesHueLookup && hues.contentEquals(other.hues) && cusp.contentDeepEquals(other.cusp) && reach.contentEquals(other.reach) && hueSearchRange.contentEquals(other.hueSearchRange)

    override fun hashCode(): Int {
        var result = hues.contentHashCode()
        result = 31 * result + cusp.contentDeepHashCode()
        result = 31 * result + reach.contentHashCode()
        return 31 * result + hueSearchRange.contentHashCode()
    }
}
