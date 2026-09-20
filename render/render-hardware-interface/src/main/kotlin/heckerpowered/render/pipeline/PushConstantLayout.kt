/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

import heckerpowered.render.shader.ShaderStage
import java.util.*

/**
 * Collects the parameter ranges through which a pipeline's shaders receive push-constant values.
 *
 * A shader describes those values as members of a block; [PushConstantRange] shows a transform
 * and color example. A range identifies the block's byte interval and the stages that may read
 * it. This layout combines those declarations for [PipelineLayoutDescription.pushConstants],
 * so shader accesses and recorded parameter updates can be checked against the same interface.
 *
 * For example, if Vertex and Fragment both use the complete 80-byte block, the layout can contain
 * one range with both stages and the interval `[0, 80)`. If they use different intervals, separate
 * ranges describe those requirements. Different draws can then supply different values through
 * `pushConstants` commands without changing the layout or selecting another descriptor set.
 * The layout stores declarations, not the current parameter values; it does not initialize data.
 *
 * Each stage appears in at most one range, following the ordinary Vulkan-compatible declaration
 * model. The RHI retains that form so a backend need not merge separate ranges for one stage,
 * which could expose gaps and change which writes overlap that stage's range. All members used
 * by a stage fit within its one interval, including intervening padding; individual members can
 * still be updated without rewriting the whole range.
 *
 * Ranges belonging to different stages may overlap. The overlap then participates in updates for
 * all stages exposing it under this layout, rather than being updated as unrelated stage-private
 * data. [validateWrite] explains this update contract and how to split a write when the set of
 * participating stages changes across the byte interval.
 *
 * @throws IllegalArgumentException if a shader stage occurs in more than one range.
 */
class PushConstantLayout(ranges: List<PushConstantRange>) {
    /**
     * Declarations for the pipeline's directly supplied parameters, copied into a stable order.
     *
     * List position does not give a range a binding number or change its byte offset. An empty
     * list exposes no push constants. Equality compares the exact declarations, independently
     * of their input order; splitting or merging stage groups is not treated as equivalent
     * merely because the covered bytes match.
     */
    val ranges: List<PushConstantRange> = Collections.unmodifiableList(
        ranges.sortedWith(compareBy<PushConstantRange> { it.offsetBytes }
            .thenBy { it.sizeBytes }
            .thenBy { range -> range.stages.map { it.name }.sorted().joinToString(",") }),
    )

    /**
     * Byte boundary immediately after the highest exposed byte; zero for an empty layout.
     *
     * A single range `[48, 80)` requires an address space extending to byte 80, not just 32
     * bytes, because offsets are not compacted. This bound is what must fit the device's
     * push-constant capacity; summing the range lengths could ignore gaps or count overlaps twice.
     */
    val sizeBytes: Int = this.ranges.maxOfOrNull { it.offsetBytes + it.sizeBytes } ?: 0

    init {
        val seenStages = mutableSetOf<ShaderStage>()
        for (range in this.ranges) {
            for (stage in range.stages) {
                require(seenStages.add(stage)) { "Shader stage $stage occurs in more than one push constant range" }
            }
        }
    }

    /**
     * Checks whether one parameter update agrees with the byte ranges and stage visibility
     * established by this layout. It does not read host memory or record the update.
     *
     * A push-constant command supplies one byte sequence to one destination interval for all
     * stages named by that command. Every named stage must expose the entire interval so the
     * update does not address bytes outside that stage's declared parameter interface.
     *
     * The command must also name every stage whose range overlaps any updated byte. This keeps
     * the recipients of an update consistent with the consumers declared for that interval:
     * within this layout, an update to overlapping bytes supplies the new bytes to all of them,
     * not only to one stage while another retains earlier values.
     *
     * These two conditions follow the ordinary Vulkan push-constant update contract. Retaining
     * that contract lets the backend use the established pipeline layout for the update, without
     * silently changing its stage set or remapping shader offsets. They are API requirements,
     * not a claim that every GPU physically stores all stages' values in one shared allocation.
     *
     * For example, two stage declarations produce three update regions:
     *
     * ```
     * Vertex:   [0, 64)
     * Fragment:         [48, 80)
     *
     * update [0, 48)  -> Vertex
     * update [48, 64) -> Vertex + Fragment
     * update [64, 80) -> Fragment
     * ```
     *
     * Updating `[48, 64)` for Vertex alone omits a declared consumer of those bytes, so it is
     * rejected. Adding Fragment to a write of `[0, 64)` is not a solution: Fragment has no declared
     * access to `[0, 48)`. Split the write at 48 so each command updates bytes exposed to its
     * complete stage set. Parameters that need independent per-stage updates can instead occupy
     * disjoint byte intervals, with matching offsets in the shader declarations.
     *
     * [offsetBytes] is absolute within the push-constant parameter area. Device capacity,
     * compatibility with the pipeline used by a draw, and initialization of shader-read bytes
     * are checked by the creation or command layer rather than by this method.
     *
     * @throws IllegalArgumentException if stages are empty, the interval is invalid or unaligned,
     * it exceeds a named stage's range, or it omits a stage that exposes updated bytes.
     */
    fun validateWrite(stages: Set<ShaderStage>, offsetBytes: Int, sizeBytes: Int) {
        require(stages.isNotEmpty()) { "Push constant write requires at least one shader stage" }
        require(offsetBytes >= 0 && offsetBytes % 4 == 0) { "Push constant write offset must be non-negative and four-byte aligned" }
        require(sizeBytes > 0 && sizeBytes % 4 == 0) { "Push constant write size must be positive and four-byte aligned" }
        require(sizeBytes <= Int.MAX_VALUE - offsetBytes) { "Push constant write end exceeds Int capacity" }

        val endBytes = offsetBytes + sizeBytes

        for (stage in stages) {
            val range = ranges.firstOrNull { stage in it.stages }
            require(
                range != null && offsetBytes >= range.offsetBytes &&
                        endBytes <= range.offsetBytes + range.sizeBytes
            ) { "Push constant write [$offsetBytes, $endBytes) is not fully exposed to $stage" }
        }
        for (range in ranges) {
            val overlaps = offsetBytes < range.offsetBytes + range.sizeBytes && range.offsetBytes < endBytes
            require(!overlaps || stages.containsAll(range.stages)) { "Push constant write omits stages ${range.stages - stages} that expose updated bytes" }
        }
    }

    override fun equals(other: Any?): Boolean = other is PushConstantLayout && ranges == other.ranges

    override fun hashCode(): Int = ranges.hashCode()
}
