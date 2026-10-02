/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.Matrices
import heckerpowered.math.MatrixView
import heckerpowered.math.times
import heckerpowered.math.toMatrix4
import heckerpowered.render.command.pass.Viewport
import heckerpowered.render.engine.material.parameter.NumericValue
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.pipeline.depthstencil.CompareFunction

/**
 * Supplies the view transform and shared shader parameters used to prepare a raster pass.
 *
 * Several object submissions can share one view while retaining different local placements.
 * [forObject] combines each placement with [worldToClip] and supplies the current viewport size,
 * so an object renderer need not build its own camera or projection matrices.
 *
 * Construction copies the Double matrix values. Object-to-clip composition also uses Double;
 * only the final shader payload is converted to Float. This preserves camera-relative precision
 * when large world translations cancel, but does not recover precision already lost in the
 * supplied matrix. Input sampling does not synchronize concurrent changes.
 *
 * [width] and [height] describe the view's framebuffer extent. The consuming pass resolves its
 * actual viewport separately and supplies it to [forObject], including when drawing into a
 * smaller render area. [depthCompare] is supplied to the selected output policy, which decides
 * how the submission's depth mode is implemented.
 *
 * @throws IllegalArgumentException if either framebuffer extent is nonpositive.
 */
class ViewParameters(
    worldToClip: MatrixView,
    val width: Int,
    val height: Int,
    val depthCompare: CompareFunction,
    val parameters: ParameterValues = ParameterValues(),
) {
    val worldToClip: MatrixView = Matrices.copyOf(worldToClip)
    internal val frustum: Frustum by lazy { Frustum.fromWorldToClip(this.worldToClip) }

    init {
        require(width > 0 && height > 0)
    }

    /**
     * Derives shader parameters for one local-space placement without changing the shared values.
     *
     * Replaces `clipFromLocal` with `worldToClip * localToWorld`, `localToWorld` with the object's
     * placement, and `viewportSize` with the resolved [viewport] extent. Both
     * matrices are packed as 16 column-major Float values. These three names are reserved here:
     * existing values with those names are replaced, while all other view parameters are retained.
     */
    fun forObject(objectState: ObjectSubmitContext, viewport: Viewport): ParameterValues {
        val model = objectState.localToWorld.toMatrix4()
        val clip = worldToClip * model
        return parameters
            .replacing("clipFromLocal", NumericValue.floats(*columnMajorFloats(clip)))
            .replacing("localToWorld", NumericValue.floats(*columnMajorFloats(model)))
            .replacing("viewportSize", NumericValue.floats(viewport.width, viewport.height))
    }

    companion object {
        /** Takes an independent Double snapshot before later object composition and shader packing. */
        fun fromClipMatrix(matrix: MatrixView, width: Int, height: Int, depthCompare: CompareFunction, parameters: ParameterValues = ParameterValues()): ViewParameters =
            ViewParameters(matrix, width, height, depthCompare, parameters)
    }
}

private fun columnMajorFloats(matrix: MatrixView): FloatArray = FloatArray(16) { index -> matrix[index % 4, index / 4].toFloat() }
