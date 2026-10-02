/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.prepare

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.draw.IndexInput
import heckerpowered.render.engine.geometry.UploadData
import heckerpowered.render.engine.geometry.index.IndexSource
import heckerpowered.render.engine.geometry.vertex.VertexStreamSource
import heckerpowered.render.engine.image.RenderImageStore
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.TextureParameterValue
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.resource.buffer.createBuffer
import heckerpowered.render.resource.lifetime
import java.util.*

/**
 * Resolves resident resources and host byte snapshots into usable bindings for one pass.
 *
 * Resident views keep their existing storage. Host data receives buffers registered with
 * [lifetime] and uploads that the executor records before entering the pass. The same [UploadData]
 * object and buffer-usage role reuse one allocation within this preparation; equal bytes in distinct
 * snapshots are independent, and different roles use separate allocations.
 *
 * [snapshot] copies the pending upload list without ending collection. The caller must retain
 * [lifetime] through GPU completion and ensure resident resources remain valid independently.
 */
internal class PassPreparation(
    val device: GraphicsDevice,
    val lifetime: ResourceLifetime,
    val targetAlphas: Map<Int, AlphaQuantity> = emptyMap(),
    private val images: RenderImageStore? = null,
) {
    private val uploadViews = IdentityHashMap<UploadData, MutableMap<BufferUsage, GpuBufferView>>()
    private val uploads = ArrayList<BufferUpload>()

    fun validateTexture(texture: TextureParameterValue) {
        val owned = images?.find(texture.view.texture) ?: return
        require(texture.alphaQuantity == owned.alphaQuantity && texture.representation == AlphaRepresentation.Premultiplied) { "Owned image sampling must retain its allocation alpha contract and RGB association" }
    }

    fun upload(bytes: UploadData, role: BufferUsage): GpuBufferView {
        require(bytes.sizeBytes > 0)
        val roles = uploadViews.getOrPut(bytes) { LinkedHashMap() }
        return roles.getOrPut(role) {
            val buffer = device.createBuffer("prepared $role", bytes.sizeBytes.toLong(), role, BufferUsage.TransferDestination)
                .lifetime(lifetime)
            val view = GpuBufferView(buffer, 0, bytes.sizeBytes.toLong())
            uploads += BufferUpload(view, bytes)
            view
        }
    }

    fun uniform(value: NumericParameterValue): GpuBufferView = upload(UploadData(value.bytes()), BufferUsage.Uniform)

    fun stream(source: VertexStreamSource): GpuBufferView = when (source) {
        is VertexStreamSource.Resident -> source.view
        is VertexStreamSource.Upload -> upload(source.bytes, BufferUsage.Vertex)
    }

    fun indices(source: IndexSource?): IndexInput? = when (source) {
        null -> null
        is IndexSource.Resident -> IndexInput(source.selection.view, source.format)
        is IndexSource.Upload -> IndexInput(upload(source.bytes, BufferUsage.Index), source.format)
    }

    fun snapshot(): List<BufferUpload> = uploads.toList()
}
