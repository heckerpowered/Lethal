/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.mesh

import heckerpowered.math.BoxView
import heckerpowered.math.Geometry
import heckerpowered.math.transformedBy
import heckerpowered.render.engine.material.Material
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderElement
import heckerpowered.render.engine.view.Frustum
import heckerpowered.render.pipeline.primitive.PrimitiveState
import java.util.*

/**
 * A pass-agnostic mesh rendering contribution.
 *
 * A batch combines [vertexInput] with appearance, primitive interpretation, and draw ranges needed
 * by later mesh passes. It deliberately stops before selecting a concrete shader, render pipeline,
 * descriptor set, attachment configuration, or other pass-specific RHI state. A mesh-pass
 * processor owns that lowering for the pass it represents.
 *
 * [elements] selects the draws that share [vertexInput], [material], and [primitive]. Keeping these
 * properties on the batch allows several ranges or instances to share one high-level rendering
 * interpretation without treating each eventual RHI draw as an unrelated scene contribution.
 *
 * [bounds] is the conservative local-space extent of this contribution and therefore carries the
 * authority for bounds-based rejection. `null` means the producer cannot prove a conservative
 * bound and the contribution must not be rejected by bounds-based culling. The bound is explicit
 * because vertex input need not have an intrinsic spatial interpretation.
 *
 * The bound is snapshotted on construction so later mutation of a live [BoxView] cannot change a
 * previously submitted contribution's visibility result.
 */
class MeshBatch(
    val vertexInput: VertexInput,
    val material: Material,
    val primitive: PrimitiveState,
    elements: List<MeshBatchElement>,
    bounds: BoxView?,
) : RenderElement {
    val elements: List<MeshBatchElement> =
        Collections.unmodifiableList(ArrayList(elements))

    val bounds: BoxView? = bounds?.let(::snapshotBox)

    init {
        this.elements.forEach { element -> validateElement(vertexInput, element) }
    }

    /**
     * Creates a batch from a reusable [Mesh], using its asset bounds as the default culling bound.
     *
     * Pass `null` explicitly when the material or another rendering technique can move geometry
     * outside [Mesh.bounds] and no conservative replacement bound is available.
     */
    constructor(mesh: Mesh, material: Material, primitive: PrimitiveState, elements: List<MeshBatchElement>, bounds: BoxView? = mesh.bounds) :
            this(mesh.vertexInput, material, primitive, elements, bounds)
}

/**
 * Tests only this mesh contribution.
 *
 * A false result permits dropping this [MeshBatch] and nothing else. In particular, it must not
 * suppress sibling render elements emitted by the same object. A missing conservative bound means
 * invisibility cannot be proven, so the batch remains visible.
 */
internal fun MeshBatch.isVisibleIn(frustum: Frustum, context: ObjectSubmitContext): Boolean {
    val bounds = bounds ?: return true
    return frustum.intersects(bounds.transformedBy(context.localToWorld))
}

private fun validateElement(vertexInput: VertexInput, element: MeshBatchElement) {
    when (element) {
        is VertexMeshBatchElement -> vertexInput.state.validateDrawInputs(vertexInput.buffers, element.vertexCount, element.firstVertex, element.instanceCount, element.firstInstance)
        is IndexedMeshBatchElement -> vertexInput.state.validateIndexedDrawInputs(vertexInput.buffers, element.indexCount, element.instanceCount, element.firstInstance)
    }
}

private fun snapshotBox(box: BoxView): BoxView {
    val minX = box.minX
    val minY = box.minY
    val minZ = box.minZ
    val maxX = box.maxX
    val maxY = box.maxY
    val maxZ = box.maxZ
    return Geometry.box(minX, minY, minZ, maxX, maxY, maxZ)
}
