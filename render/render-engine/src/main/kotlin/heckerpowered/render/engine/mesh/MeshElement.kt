/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.mesh

import heckerpowered.math.transformedBy
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderElement
import heckerpowered.render.engine.view.Frustum

class MeshElement(
    val mesh: Mesh,
) : RenderElement

/**
 * Tests only this mesh contribution.
 *
 * A false result permits dropping this [MeshElement] and nothing else. In
 * particular, it must not suppress sibling elements emitted by the same
 * ObjectRenderer.
 */
internal fun MeshElement.isVisibleIn(frustum: Frustum, context: ObjectSubmitContext): Boolean =
    frustum.intersects(mesh.bounds.transformedBy(context.localToWorld))