/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.depthstencil

/**
 * Describes stencil testing and updates for one polygon-face orientation.
 *
 * The stencil comparison uses the current stencil reference value and the value stored in the
 * stencil attachment after the outer stencil state's read mask has been applied.
 *
 * The selected operation depends on both stencil and depth testing:
 *
 * | Stencil comparison | Depth comparison   | Selected operation          |
 * |--------------------|--------------------|-----------------------------|
 * | Fails              | Not evaluated      | [stencilFailureOperation]   |
 * | Passes             | Fails              | [depthFailureOperation]     |
 * | Passes             | Passes or disabled | [passOperation]             |
 *
 * A selected operation may update stencil even when the fragment does not reach color output.
 * Every update is restricted by the write mask of the outer stencil state.
 *
 * Typical configurations include:
 *
 * - `Always` with `Replace` as [passOperation] to mark visible samples.
 * - `Equal` with every operation set to [StencilOperation.Keep] to use stencil as a read-only mask.
 * - Incrementing or decrementing on [depthFailureOperation] when counting occluded volume
 *   intersections.
 *
 * This state describes one face orientation only. The containing stencil state assigns separate
 * instances to front-facing and back-facing polygons.
 */
data class StencilFaceState(
    /**
     * Determines whether the masked stencil reference and stored stencil value pass the test.
     *
     * When the comparison fails, depth testing is not evaluated and
     * [stencilFailureOperation] is selected.
     */
    val compareFunction: CompareFunction = CompareFunction.Always,

    /**
     * Applied when the stencil comparison fails.
     *
     * The fragment cannot update color or depth, but this operation may still update stencil.
     */
    val stencilFailureOperation: StencilOperation = StencilOperation.Keep,

    /**
     * Applied when stencil testing passes but depth testing fails.
     *
     * This operation is never selected when depth testing is disabled.
     */
    val depthFailureOperation: StencilOperation = StencilOperation.Keep,

    /**
     * Applied when stencil testing passes and depth testing either passes or is disabled.
     *
     * [StencilOperation.Replace] is commonly used here to mark samples that survive visibility
     * testing.
     */
    val passOperation: StencilOperation = StencilOperation.Keep,
)