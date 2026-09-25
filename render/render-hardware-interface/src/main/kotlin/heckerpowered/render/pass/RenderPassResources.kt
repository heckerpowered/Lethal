/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.binding.*
import heckerpowered.render.buffer.BufferUsage
import heckerpowered.render.buffer.GpuBufferView
import heckerpowered.render.command.ImageRegion
import heckerpowered.render.memory.Size
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.texture.GpuTextureView
import heckerpowered.render.texture.TextureViewDescription
import java.util.*

/**
 * Declares the shader and geometry resources that a render pass may access before drawing begins.
 *
 * A scene can switch between brick and wood materials inside one pass. Listing both materials
 * here lets the backend prepare their images before entering native rendering; the bind commands
 * still choose which material each draw uses. The callback runs once and can encode directly,
 * without first being recorded and replayed to discover its resources.
 *
 * This is an upper bound on access, not a schedule. A listed material need not be drawn, but its
 * declared ranges, roles, stages, and storage permissions participate in preparation and scope
 * compatibility checks. Prefer the resources relevant to this pass over a list of every asset.
 * Declaring a write does not initialize data or prove that any shader actually wrote it.
 *
 * Attachments and color resolves already belong to [RenderPassDescription] and are not repeated
 * here. Extra shader access to attachment storage still needs a descriptor declaration and a
 * supported attachment-read or feedback mechanism; attachment selection alone does not grant it.
 *
 * Collections are immutable snapshots. The same declaration can be reused, but every invocation
 * must prepare its resources against the then-current execution stream. This value contains no
 * native layout, completion marker, binding state, or cached result of a previous preparation.
 * Resource contents and imported access scopes are not snapshotted or extended.
 *
 * Preparation establishes dependencies at the pass boundary. It does not order arbitrary storage
 * writes and reads between draws within the pass. Such dependencies need an explicitly supported
 * mechanism or separate passes; fixed-function attachment operations retain their own semantics.
 *
 * @throws IllegalArgumentException if a geometry range is empty or lacks its declared usage,
 * or a shader view has an invalid subresource selection.
 */
class RenderPassResources(
    descriptors: List<DescriptorSet> = emptyList(),
    vertexBuffers: List<GpuBufferView> = emptyList(),
    indexBuffers: List<GpuBufferView> = emptyList(),
) {
    /**
     * Potential shader-resource groups, not bindings assigned by list position.
     *
     * Each group's layout supplies roles, stage visibility, and storage permissions; its actual
     * resources supply ranges. All binding array elements participate, regardless of which shader
     * later consumes them. The set number is still selected by RenderPass.bindDescriptorSet.
     * Samplers remain available for device validation but cause no image-content access themselves.
     */
    val descriptors: List<DescriptorSet> = snapshot(descriptors)

    /**
     * Ranges permitted for vertex and instance attribute fetches, not numbered vertex slots.
     *
     * A binding can select a subrange or a range covered by several adjacent declarations. Holes
     * are not filled by taking a bounding interval. Storage or uniform reads of these same bytes
     * do not replace this vertex-input declaration. Include device-provided primitive buffers too.
     */
    val vertexBuffers: List<GpuBufferView> = snapshot(vertexBuffers)

    /**
     * Ranges permitted for index fetches. The bind command separately chooses the index format.
     * A range listed only as vertex input does not also permit index input.
     */
    val indexBuffers: List<GpuBufferView> = snapshot(indexBuffers)

    // A fixed projection of the input, not a history of bindings or resource transitions.
    private val shaderAccesses: List<ShaderResourceAccess> = collectShaderAccesses(this.descriptors)

    init {
        this.vertexBuffers.forEach { validateGeometryRange(it, BufferUsage.Vertex) }
        this.indexBuffers.forEach { validateGeometryRange(it, BufferUsage.Index) }
    }

    /**
     * Visits every supplied shader resource with the declaration that explains its access.
     *
     * Backends use this together with [vertexBuffers], [indexBuffers], and the pass description
     * before native rendering begins. Array elements are visited individually, including samplers.
     * Original views and combined image/sampler pairs are retained for import-scope validation.
     *
     * Entries are not a last-use table: repeated or overlapping selections may carry different
     * stages and permissions. Accumulate all relevant accesses without filling range holes or
     * forming a cross-product of permissions that were declared for different stages. Native
     * layout compatibility, aliases, and synchronization granularity remain backend decisions.
     */
    fun forEachShaderResource(action: (DescriptorBindingLayout, DescriptorResource) -> Unit) {
        descriptors.forEach { it.forEachShaderResource(action) }
    }

    /**
     * Checks metadata known at the pass entrance, including resolve-destination exclusivity.
     *
     * A shader view exposes complete texels of its selected mips and layers. It cannot overlap a
     * color resolve destination reserved by this pass, even when application control flow would
     * skip that material. Attachments, resolves, and this declaration form one scope request.
     *
     * This is not complete scope compatibility validation. The backend must resolve opaque
     * attachments and physical aliases, check every retained import reference and resource,
     * validate native combinations (including input attachments and feedback), and establish
     * dependencies before entering rendering. None of these checks establishes image contents.
     *
     * @throws IllegalArgumentException if attachment metadata is invalid or a declared shader
     * view overlaps a resolve destination identifiable through the same texture reference.
     */
    fun validateFor(description: RenderPassDescription) {
        description.validateAttachments()
        description.colorResolves.forEach { resolve ->
            shaderAccesses.forEach { access ->
                val view = access.textureView
                require(view == null || !overlapsKnownTexture(view, resolve.destination)) {
                    "Shader resource overlaps the resolve destination of color attachment ${resolve.colorAttachment}"
                }
            }
        }
    }

    /**
     * Checks that every content access exposed by a binding is covered by this pass declaration.
     *
     * Coverage is by resource range, role, shader stage, and read/write permission, not descriptor
     * object identity, label, binding number, or set number. Separate and combined sampled-image
     * slots have the same image-access role. Uniform/storage and sampled/input-attachment roles
     * are distinct. Each stage and access direction must cover the entire requested selection;
     * Vertex reads plus Fragment writes do not authorize Vertex writes.
     *
     * Call this before changing the native binding, including bindings not used by a later draw.
     * It does not replace DescriptorSet.validateFor: the selected pipeline still checks slot
     * kinds, formats, dimensions, counts, and visibility. Samplers have no content range to cover
     * and may change without a new access declaration, but still require device and pairing checks.
     *
     * Metadata coverage uses the same GpuBuffer/GpuTexture reference. Views of that texture may
     * differ. Different resource wrappers are not assumed to be equivalent, even if equals says
     * so. Declare the actual wrapper to use; the backend additionally checks physical aliasing
     * and the validity of both declared and bound views. Coverage never grants an import permission.
     *
     * @throws IllegalArgumentException if any resource selection, role, stage, or permission
     * extends beyond the declared coverage, or a bound view has invalid metadata.
     */
    fun validateDescriptorSet(descriptors: DescriptorSet) {
        collectShaderAccesses(listOf(descriptors)).forEach { requested ->
            requested.declaration.stages.forEach { stage ->
                if (requested.reads) validateShaderAccess(requested, stage, write = false)
                if (requested.writes) validateShaderAccess(requested, stage, write = true)
            }
        }
    }

    /**
     * Checks vertex-input coverage before a bind changes native state. It includes instance-rate
     * data, but not stride, attribute format, or the actual elements selected by a draw.
     *
     * @throws IllegalArgumentException if the range is empty, lacks Vertex usage, or contains
     * bytes not declared for vertex input on the same buffer reference.
     */
    fun validateVertexBuffer(view: GpuBufferView) {
        validateGeometryRange(view, BufferUsage.Vertex)
        require(coversBuffer(view, vertexBuffers)) { "Vertex binding extends beyond the pass's declared vertex ranges" }
    }

    /**
     * Checks index-input coverage. Index-format alignment and draw bounds are checked separately.
     *
     * @throws IllegalArgumentException if the range is empty, lacks Index usage, or contains
     * bytes not declared for index input on the same buffer reference.
     */
    fun validateIndexBuffer(view: GpuBufferView) {
        validateGeometryRange(view, BufferUsage.Index)
        require(coversBuffer(view, indexBuffers)) { "Index binding extends beyond the pass's declared index ranges" }
    }

    private fun validateShaderAccess(requested: ShaderResourceAccess, stage: ShaderStage, write: Boolean) {
        val candidates = shaderAccesses.filter {
            it.role == requested.role && stage in it.declaration.stages && if (write) it.writes else it.reads
        }
        val buffer = requested.bufferView
        val covered = if (buffer != null) {
            coversBuffer(buffer, candidates.mapNotNull { it.bufferView })
        } else {
            coversTexture(checkNotNull(requested.textureView), candidates.mapNotNull { it.textureView })
        }
        require(covered) {
            "Binding ${requested.declaration.binding} ${requested.role} ${if (write) "write" else "read"} " +
                    "in $stage extends beyond this pass's declared resource coverage"
        }
    }

    companion object {
        /** No additional shader or geometry accesses; attachments still come from the pass description. */
        val Empty = RenderPassResources()
    }
}

private fun <T> snapshot(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

private fun DescriptorSet.forEachShaderResource(action: (DescriptorBindingLayout, DescriptorResource) -> Unit) {
    bindings.forEachIndexed { index, binding ->
        val declaration = layout.bindings[index]
        binding.resources.forEach { action(declaration, it) }
    }
}

private enum class ResourceRole {
    UniformBuffer, StorageBuffer, UniformTexelBuffer, StorageTexelBuffer,
    SampledTexture, StorageTexture, InputAttachment,
}

private class ShaderResourceAccess(
    val declaration: DescriptorBindingLayout,
    val resource: DescriptorResource,
    val role: ResourceRole,
    access: StorageAccess = StorageAccess.ReadOnly,
) {
    val reads: Boolean = access != StorageAccess.WriteOnly
    val writes: Boolean = access != StorageAccess.ReadOnly

    val bufferView: GpuBufferView?
        get() = when (resource) {
            is DescriptorResource.Buffer -> resource.view
            is DescriptorResource.TexelBuffer -> resource.view
            else -> null
        }

    val textureView: GpuTextureView?
        get() = when (resource) {
            is DescriptorResource.Texture -> resource.view
            is DescriptorResource.CombinedTextureSampler -> resource.view
            else -> null
        }
}

private fun collectShaderAccesses(descriptors: List<DescriptorSet>): List<ShaderResourceAccess> {
    val result = ArrayList<ShaderResourceAccess>()
    descriptors.forEach { set ->
        set.forEachShaderResource { declaration, resource ->
            val access = shaderAccess(declaration, resource)
            if (access != null) {
                access.textureView?.let(::validateViewSelection)
                result.add(access)
            }
        }
    }
    return result
}

private fun shaderAccess(declaration: DescriptorBindingLayout, resource: DescriptorResource): ShaderResourceAccess? =
    when (val type = declaration.type) {
        is DescriptorType.UniformBuffer -> ShaderResourceAccess(declaration, resource, ResourceRole.UniformBuffer)
        is DescriptorType.StorageBuffer -> ShaderResourceAccess(declaration, resource, ResourceRole.StorageBuffer, type.access)
        is DescriptorType.UniformTexelBuffer -> ShaderResourceAccess(declaration, resource, ResourceRole.UniformTexelBuffer)
        is DescriptorType.StorageTexelBuffer -> ShaderResourceAccess(declaration, resource, ResourceRole.StorageTexelBuffer, type.access)
        is DescriptorType.SampledTexture -> ShaderResourceAccess(declaration, resource, ResourceRole.SampledTexture)
        is DescriptorType.CombinedTextureSampler -> ShaderResourceAccess(declaration, resource, ResourceRole.SampledTexture)
        is DescriptorType.StorageTexture -> ShaderResourceAccess(declaration, resource, ResourceRole.StorageTexture, type.access)
        is DescriptorType.InputAttachment -> ShaderResourceAccess(declaration, resource, ResourceRole.InputAttachment)
        DescriptorType.Sampler -> null
    }

private fun validateGeometryRange(view: GpuBufferView, usage: BufferUsage) {
    require(view.sizeBytes > 0) { "Pass geometry declarations require a non-empty buffer range" }
    require(usage in view.buffer.usage) { "Pass geometry declaration requires BufferUsage.$usage" }
}

private fun validateViewSelection(view: GpuTextureView) {
    TextureViewDescription(
        dimension = view.dimension,
        aspects = view.aspects,
        baseMipLevel = view.baseMipLevel,
        mipLevelCount = view.mipLevelCount,
        baseArrayLayer = view.baseArrayLayer,
        arrayLayerCount = view.arrayLayerCount,
    ).validateFor(view.texture)
    require(view.format == view.texture.format) { "A pass shader view must preserve its texture's format" }
}

private data class Interval(val start: Size, val end: Size)

private fun coversBuffer(requested: GpuBufferView, declared: List<GpuBufferView>): Boolean = coversInterval(
    Interval(requested.offsetBytes, requested.offsetBytes + requested.sizeBytes),
    declared.filter { it.buffer === requested.buffer }
        .map { Interval(it.offsetBytes, it.offsetBytes + it.sizeBytes) },
)

private fun coversInterval(requested: Interval, declared: List<Interval>): Boolean {
    var coveredUntil = requested.start
    for (interval in declared.sortedBy { it.start }) {
        if (interval.start > coveredUntil) return false
        coveredUntil = maxOf(coveredUntil, interval.end)
        if (coveredUntil >= requested.end) return true
    }
    return coveredUntil >= requested.end
}

private fun coversTexture(requested: GpuTextureView, declared: List<GpuTextureView>): Boolean {
    val sameTexture = declared.filter { it.texture === requested.texture }
    return requested.aspects.all { aspect ->
        coversSubresources(requested, sameTexture.filter { aspect in it.aspects })
    }
}

private fun coversSubresources(requested: GpuTextureView, declared: List<GpuTextureView>): Boolean {
    val firstMip = requested.baseMipLevel.toLong()
    val endMip = firstMip + requested.mipLevelCount
    val mipBoundaries = arrayListOf(firstMip, endMip)
    declared.forEach { view ->
        val begin = maxOf(firstMip, view.baseMipLevel.toLong())
        val end = minOf(endMip, view.baseMipLevel.toLong() + view.mipLevelCount)
        if (begin < end) {
            mipBoundaries.add(begin)
            mipBoundaries.add(end)
        }
    }

    // Check layer coverage for each constant mip band, not a bounding rectangle or every layer.
    // Combining independent mip/layer unions would falsely fill diagonal holes.
    val bands = mipBoundaries.distinct().sorted()
    val layers = Interval(requested.baseArrayLayer.toLong(), requested.baseArrayLayer.toLong() + requested.arrayLayerCount)
    return bands.zipWithNext().all { (begin, end) ->
        val coveredLayers = declared.filter {
            it.baseMipLevel <= begin && it.baseMipLevel.toLong() + it.mipLevelCount >= end
        }.map { Interval(it.baseArrayLayer.toLong(), it.baseArrayLayer.toLong() + it.arrayLayerCount) }
        coversInterval(layers, coveredLayers)
    }
}

private fun overlapsKnownTexture(view: GpuTextureView, region: ImageRegion): Boolean {
    val texture = when (region) {
        is ImageRegion.Texture -> region.texture
        is ImageRegion.View -> region.view.texture
        is ImageRegion.Attachment -> return false // Opaque selection: the device must resolve its identity.
    }
    if (view.texture !== texture || region.aspect !in view.aspects) return false
    val mip = when (region) {
        is ImageRegion.Texture -> region.mipLevel
        is ImageRegion.View -> region.textureMipLevel
        is ImageRegion.Attachment -> error("Opaque attachment has no public texture mip")
    }
    val layer = when (region) {
        is ImageRegion.Texture -> region.baseArrayLayer
        is ImageRegion.View -> region.textureBaseArrayLayer
        is ImageRegion.Attachment -> error("Opaque attachment has no public texture layer")
    }
    return mip.toLong() >= view.baseMipLevel && mip.toLong() < view.baseMipLevel.toLong() + view.mipLevelCount &&
            layer.toLong() < view.baseArrayLayer.toLong() + view.arrayLayerCount &&
            view.baseArrayLayer.toLong() < layer.toLong() + region.arrayLayerCount
}
