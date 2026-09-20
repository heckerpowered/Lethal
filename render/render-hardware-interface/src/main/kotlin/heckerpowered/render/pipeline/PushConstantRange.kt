/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

import heckerpowered.render.shader.ShaderStage
import java.util.*

/**
 * Declares which bytes of a shader's push-constant parameter block are available to particular
 * shader stages.
 *
 * Push constants supply small values directly through commands, without selecting a buffer in a
 * descriptor set. They are useful for parameters that change between draws, such as an object's
 * transform and color. For example, a Vulkan GLSL shader can declare:
 *
 * ```glsl
 * layout(push_constant, std430, column_major) uniform constants {
 *     vec4 data;
 *     mat4 render_matrix;
 * } PushConstants;
 * ```
 *
 * With these 32-bit types and this member layout, `data` occupies bytes `[0, 16)` and the matrix
 * occupies `[16, 80)`, as four columns of four floats. If the vertex shader uses both members,
 * one declaration exposing the complete block is:
 *
 * ```kotlin
 * PushConstantRange(
 *     stages = setOf(ShaderStage.Vertex),
 *     offsetBytes = 0,
 *     sizeBytes = 80,
 * )
 * ```
 *
 * The shader declaration assigns types and member offsets to the bytes. This range tells the
 * pipeline interface that Vertex may read those bytes. A later `pushConstants` command supplies
 * the actual values, packed to match the shader, so it can read `PushConstants.data` and
 * `PushConstants.render_matrix`. Creating the range neither uploads values nor generates the
 * shader block.
 *
 * A range describes accessible bytes, not one indivisible update. After initializing this block,
 * an update to `[0, 16)` can replace `data` without uploading the matrix again, subject to the
 * stage-visibility rules of the enclosing [PushConstantLayout]. That layout combines the ranges
 * needed by all stages of a pipeline.
 *
 * @throws IllegalArgumentException if visibility is empty, the offset is negative, the size is
 * not positive, the offset or size is not a multiple of four bytes, or the end cannot be represented.
 */
class PushConstantRange(
    stages: Set<ShaderStage>,

    /**
     * First exposed byte, measured from the beginning of the push-constant parameter area.
     *
     * The offset is absolute within that area, not relative to another range or a host pointer.
     * It must be a multiple of four bytes so the declaration uses the same word-sized granularity
     * as push-constant updates. Shader members may require stricter alignment.
     */
    val offsetBytes: Int,

    /**
     * Length of the exposed interval in bytes, including any padding between shader members.
     *
     * The interval is `[offsetBytes, offsetBytes + sizeBytes)`. For the block above, 80 bytes
     * cover both `data` and `render_matrix`; this is not a count of fields or floating-point
     * components. The size must be a positive multiple of four bytes.
     */
    val sizeBytes: Int,
) {
    /**
     * Shader stages allowed to read this interval.
     *
     * When Vertex and Fragment both need the same interval, one range can name both. The
     * enclosing layout uses this visibility to check that shader reads and parameter updates
     * stay within the interface established for those stages.
     */
    val stages: Set<ShaderStage> = Collections.unmodifiableSet(stages.toSet())

    init {
        require(this.stages.isNotEmpty()) { "Push constant range requires shader-stage visibility" }
        require(offsetBytes >= 0 && offsetBytes % 4 == 0) { "Push constant offset must be non-negative and four-byte aligned" }
        require(sizeBytes > 0 && sizeBytes % 4 == 0) { "Push constant size must be positive and four-byte aligned" }
        // Validate by subtraction before forming an end offset from caller-provided values.
        require(sizeBytes <= Int.MAX_VALUE - offsetBytes) { "Push constant range end exceeds Int capacity" }
    }

    override fun equals(other: Any?): Boolean =
        other is PushConstantRange && stages == other.stages &&
                offsetBytes == other.offsetBytes && sizeBytes == other.sizeBytes

    override fun hashCode(): Int = 31 * (31 * stages.hashCode() + offsetBytes) + sizeBytes
}
