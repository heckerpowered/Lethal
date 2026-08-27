/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.depthstencil

/**
 * Selects the comparison used by a depth or stencil test.
 *
 * Every entry compares a reference value against a test value:
 *
 * ```
 * comparison(referenceValue, testValue)
 * ```
 *
 * The meaning of those operands depends on the test:
 *
 * | Test | `referenceValue` | `testValue` |
 * | --- | --- | --- |
 * | Depth | Incoming fragment depth | Depth currently stored in the attachment |
 * | Stencil | Current stencil reference after masking | Stored stencil value after masking |
 *
 * A successful comparison allows the sample to continue to later fragment operations. A failed
 * comparison rejects it from those later operations, but may still select a stencil failure
 * operation.
 *
 * This comparison only determines pass or failure. Depth writes are controlled separately by
 * [DepthState.writeEnabled], while stencil updates are selected by [StencilFaceState].
 */
enum class CompareFunction {
    /**
     * Always fails the comparison.
     *
     * This does not necessarily make the draw a complete no-op. A failed stencil or depth test may
     * still select an operation that updates the stencil value.
     */
    Never,

    /**
     * Passes when:
     *
     * ```
     * referenceValue < testValue
     * ```
     *
     * This is the conventional depth comparison when nearer fragments produce smaller depth
     * values. Equal values fail, which avoids repeatedly accepting an already stored surface.
     */
    Less,

    /**
     * Passes when:
     *
     * ```
     * referenceValue == testValue
     * ```
     *
     * This is commonly used to select pixels carrying an exact stencil label. For depth, it should
     * only be used when both passes are expected to produce exactly the same stored depth; this
     * comparison does not provide an approximate tolerance.
     */
    Equal,

    /**
     * Passes when:
     *
     * ```
     * referenceValue <= testValue
     * ```
     *
     * Compared with [Less], this also accepts a surface whose depth equals the stored value. It is
     * useful for later passes that intentionally redraw the same geometry, but does not compensate
     * for differing depth calculations or ordinary z-fighting.
     */
    LessOrEqual,

    /**
     * Passes when:
     *
     * ```
     * referenceValue > testValue
     * ```
     *
     * This is the usual comparison for reverse-depth rendering, where nearer fragments produce
     * greater depth values and the attachment is normally cleared to its minimum depth.
     */
    Greater,

    /**
     * Passes when:
     *
     * ```
     * referenceValue != testValue
     * ```
     *
     * This is particularly useful for excluding a stencil-marked region. For example, expanded
     * geometry can be drawn only where the stored stencil value differs from the label written by
     * the original geometry, producing an outline around its silhouette.
     */
    NotEqual,

    /**
     * Passes when:
     *
     * ```
     * referenceValue >= testValue
     * ```
     *
     * This is the reverse-depth counterpart of [LessOrEqual], allowing later passes to redraw
     * geometry whose depth exactly matches the stored surface.
     */
    GreaterOrEqual,

    /**
     * Always passes the comparison.
     *
     * For depth, this allows every otherwise surviving sample to update depth when depth writes are
     * enabled. For stencil, it bypasses filtering by the existing stencil value while still allowing
     * the subsequent depth result and configured stencil operations to determine the update.
     */
    Always,
}