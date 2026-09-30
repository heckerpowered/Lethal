/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.mesh

import heckerpowered.math.BoxView

/**
 * Geometry that can participate in the mesh rendering pipeline.
 *
 * This is the pass-agnostic geometry side of a [MeshBatch]. It identifies the geometry feeding
 * model, while a later mesh-pass processor decides how that model is consumed by the shaders and
 * RHI state selected for a particular pass.
 *
 * [bounds] describes only the intrinsic local-space extent of the geometry. It does not by itself
 * authorize culling a submitted contribution: a material or another rendering technique may move
 * vertices outside this region. [MeshBatch.bounds] is the conservative bound that carries that
 * authority for one concrete contribution.
 *
 * The interface intentionally does not expose RHI vertex-buffer bindings yet. Static, dynamic,
 * skinned, instanced, procedural, and GPU-produced geometry need not share the same feeding model;
 * that contract should be introduced only after those cases establish the common requirements.
 */
interface MeshGeometry {
    val bounds: BoxView
}
