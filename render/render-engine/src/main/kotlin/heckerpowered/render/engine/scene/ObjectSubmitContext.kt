/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import heckerpowered.math.*

/**
 * Places an object's local coordinate system in the world.
 *
 * [localToWorld] transforms positions from the object's local coordinate
 * system into world space. View and projection transforms belong to the view rather than to the
 * object. Composition uses Double coordinates so large world translations can cancel against the
 * camera transform before the final shader values are converted to Float.
 *
 * The constructor retains the supplied transform view. [snapshot] copies its values, and
 * [RenderSubmission] takes that snapshot when capturing a placement. Sampling a mutable view does
 * not synchronize concurrent changes.
 */
class ObjectSubmitContext(
    val localToWorld: AffineTransformView,
)

/** Composes a child placement as `localToWorld * localToParent`; the child transform is applied first. */
fun ObjectSubmitContext.transformed(localToParent: AffineTransformView): ObjectSubmitContext =
    ObjectSubmitContext(localToWorld * localToParent)

fun ObjectSubmitContext.transformed(localToParent: TransformView): ObjectSubmitContext =
    transformed(localToParent.toAffine())

/** Retains independent transform values so later changes to the source view cannot move submissions. */
fun ObjectSubmitContext.snapshot(): ObjectSubmitContext =
    ObjectSubmitContext(AffineTransforms.copyOf(localToWorld))
