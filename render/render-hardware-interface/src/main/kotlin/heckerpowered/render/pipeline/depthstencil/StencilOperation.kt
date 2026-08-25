/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.depthstencil

/**
 * Selects how a stencil value is modified after stencil and depth testing.
 *
 * One operation is selected for each covered sample:
 *
 * | Test result | Selected operation |
 * | --- | --- |
 * | Stencil test fails | Stencil-failure operation |
 * | Stencil passes but depth fails | Depth-failure operation |
 * | Stencil and depth both pass | Pass operation |
 *
 * A stencil update may therefore occur even when the sample fails and produces no color output.
 *
 * The selected operation first produces an `operationResult` from the complete stored stencil
 * value. The stencil write mask is applied afterward:
 *
 * ```
 * storedStencilValue = (previousStencilValue & ~writeMask) | (operationResult & writeMask)
 * ```
 *
 * Consequently, a partial write mask preserves disabled bits but does not change how increment,
 * decrement, or inversion calculates its initial result.
 */
enum class StencilOperation {
    /**
     * Preserves the existing stencil value.
     *
     * This is the normal choice for any test outcome that should neither create nor remove stencil
     * markings.
     */
    Keep,

    /**
     * Produces zero as the new stencil value.
     *
     * Only bits enabled by the stencil write mask are cleared. Unlike clearing an attachment, this
     * operation affects only samples covered by the draw and only when its associated test outcome
     * occurs.
     */
    Zero,

    /**
     * Replaces the stencil value with the current stencil reference value.
     *
     * This is the primary operation for assigning pixel labels or flags. A partial write mask can
     * replace selected bits while preserving unrelated stencil information.
     */
    Replace,

    /**
     * Increments the stencil value and clamps at the largest value representable by the stencil
     * format.
     *
     * For an 8-bit stencil component:
     *
     * ```
     * operationResult = min(previousStencilValue + 1, 255)
     * ```
     *
     * This is useful for counting overlapping or nested volumes when overflow must not turn a
     * heavily covered pixel back into a small value. Once the maximum is reached, additional
     * increments cannot be distinguished.
     */
    IncrementAndClamp,

    /**
     * Decrements the stencil value and clamps at zero.
     *
     * ```
     * operationResult = max(previousStencilValue - 1, 0)
     * ```
     *
     * This is commonly paired with [IncrementAndClamp] when entering and leaving nested regions.
     * Once zero is reached, additional decrements have no effect.
     */
    DecrementAndClamp,

    /**
     * Bitwise-inverts every bit of the existing stencil value.
     *
     * For an 8-bit stencil component:
     *
     * ```
     * operationResult = previousStencilValue xor 0xFF
     * ```
     *
     * This is useful for parity masks and for toggling stencil flags whenever geometry covers a
     * sample. Applying it twice restores the original value. The write mask determines which of
     * the inverted bits are ultimately stored.
     */
    Invert,

    /**
     * Increments the stencil value and wraps to zero after the largest representable value.
     *
     * For an 8-bit stencil component:
     *
     * ```
     * 254 -> 255
     * 255 -> 0
     * ```
     *
     * Wrapping avoids permanent saturation in algorithms that use cyclic or balanced counts, but
     * overflow can make a heavily incremented pixel indistinguishable from an unmarked pixel.
     */
    IncrementAndWrap,

    /**
     * Decrements the stencil value and wraps from zero to the largest representable value.
     *
     * For an 8-bit stencil component:
     *
     * ```
     * 1 -> 0
     * 0 -> 255
     * ```
     *
     * This is commonly paired with [IncrementAndWrap]. It should only be used when wraparound is an
     * intentional part of the algorithm.
     */
    DecrementAndWrap,
}