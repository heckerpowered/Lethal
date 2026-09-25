/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.command

import heckerpowered.render.buffer.GpuBuffer
import heckerpowered.render.buffer.GpuBufferView
import heckerpowered.render.memory.MemoryFrame
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.memory.Size
import heckerpowered.render.pass.RenderPass
import heckerpowered.render.pass.RenderPassDescription
import heckerpowered.render.target.RenderAttachment
import heckerpowered.render.texture.TextureAspect

/**
 * Encodes commands into the active graphics recording.
 *
 * A command encoder is valid only for the scoped recording that supplies it. The graphics device owns that recording's
 * completion and cleanup; callers neither close the encoder nor retain frame-scoped values created through it.
 */
interface CommandEncoder {
    val memoryStack: MemoryStack

    /**
     * Uploads host bytes into the complete range selected by [destination].
     *
     * Use this to provide mesh data or replace a parameter block before a later draw reads it.
     * The view chooses both the buffer offset and byte count; select a subview to update only
     * part of a larger block. Bytes outside the selection retain their contents and validity.
     * This copies bytes without interpreting fields, changing byte order, or adding padding.
     *
     * Before returning normally, the implementation has consumed or independently saved all
     * source bytes. The caller can then overwrite or release the source storage, even when GPU
     * execution is deferred. This permits uploads from a temporary [MemoryStack.frame]; keeping
     * only its address would let later allocations change an already recorded upload.
     * Returning does not mean the GPU has finished writing [destination].
     *
     * [sourceAddress] must identify at least `destination.sizeBytes` readable bytes for the
     * duration of this call. They must not be concurrently modified. A nonzero address alone
     * does not prove that this memory exists or is readable; that is the caller's responsibility.
     *
     * The destination requires TransferDestination usage, independently of the roles used by
     * later consumers. An empty view transfers no bytes and does not dereference [sourceAddress],
     * which may then be zero. Recording, device, permission, and resource-validity checks still
     * apply. Uploads have byte granularity; a particular native update command's alignment or
     * inline-size limit is not an additional requirement on this interface.
     *
     * Record outside a logical render-pass callback. Conflicting accesses recorded through this
     * encoder are ordered: earlier readers see the contents before this upload, and later readers
     * see these bytes unless another intervening operation changes them. The backend must establish
     * the necessary execution and memory dependencies, not just preserve command-list order.
     * Access through other recordings, queues, or a host API needs its own synchronization contract.
     *
     * [GpuBufferView.validateUpload] checks the public metadata and null-address condition.
     * Implementations also check device identity, access scopes, and native support. Any temporary
     * upload storage must outlive the GPU work that reads it, not merely this Kotlin callback.
     *
     * @throws IllegalArgumentException if the destination belongs to another device, lacks the
     * upload usage, or a nonempty upload has a zero source address.
     * @throws UnsupportedOperationException if the device cannot perform this upload for the
     * resource and range without changing the requested behavior.
     * @throws IllegalStateException if recording is inactive, a logical render pass is active,
     * or the destination resource or access scope is invalid.
     *
     * @see <a href="https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdUpdateBuffer.html">Vulkan host-data update</a>
     * @see <a href="https://registry.khronos.org/OpenGL/extensions/ARB/ARB_vertex_buffer_object.txt">OpenGL buffer subdata</a>
     */
    fun writeBuffer(destination: GpuBufferView, sourceAddress: NativeAddress)

    /**
     * Selects a buffer range explicitly, then performs the same upload as the view-based overload.
     *
     * The offset is relative to the complete buffer, and both offset and size are byte counts.
     * This convenience form does not impose a different source-data lifetime or upload limit.
     */
    fun writeBuffer(buffer: GpuBuffer, sourceAddress: NativeAddress, sizeBytes: Size, destinationOffsetBytes: Size = 0) {
        writeBuffer(GpuBufferView(buffer, destinationOffsetBytes, sizeBytes), sourceAddress)
    }

    /**
     * Copies the selected buffer bytes to an equally sized destination range on the device.
     *
     * A staging buffer can supply a mesh without reading it back to the CPU. The source is read
     * when the copy executes in the command sequence, rather than captured when this Kotlin
     * method is called. For example, upload A, copy, then upload B to the same source range leaves
     * A in the copy destination and B in the source after those operations execute.
     *
     * Source usage must include TransferSource and destination usage TransferDestination. The
     * two views must have equal lengths; no truncation, padding, or format conversion is applied.
     * Every source byte read must be defined. Only the selected destination bytes are replaced.
     * Empty ranges access no storage, but still require valid recording, resources, and usages.
     *
     * The actual source and destination storage ranges must not overlap. Nonoverlapping ranges
     * of the same buffer are representable, but copying a nonempty range onto itself is rejected.
     * This operation does not promise memmove behavior or silently allocate a temporary buffer
     * to repair overlap. Distinct resource wrappers may still alias the same native storage.
     *
     * Record outside a logical render-pass callback. The backend orders conflicting earlier and
     * later accesses through this encoder, including reads before later overwrites, and establishes
     * the required visibility. There is no implicit CPU readback or completion wait. Independent
     * recordings and external accesses require their own synchronization contract.
     *
     * [GpuBufferView.validateCopyTo] checks lengths, usages, and overlap visible through the same
     * buffer reference. The device must also resolve physical aliases, ownership, access scopes,
     * source-content availability, and native execution requirements.
     *
     * @throws IllegalArgumentException if a resource belongs to another device, a required usage
     * is absent, view lengths differ, or the actual storage ranges overlap.
     * @throws UnsupportedOperationException if the device cannot execute the requested copy.
     * @throws IllegalStateException if recording is inactive, a logical render pass is active,
     * or a resource, access scope, or required source content is no longer valid.
     *
     * @see <a href="https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdCopyBuffer.html">Vulkan buffer copy</a>
     * @see <a href="https://registry.khronos.org/OpenGL/extensions/ARB/ARB_copy_buffer.txt">OpenGL buffer copy</a>
     * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/d3d12/nf-d3d12-id3d12graphicscommandlist-copybufferregion">Direct3D 12 buffer copy</a>
     */
    fun copyBuffer(source: GpuBufferView, destination: GpuBufferView)

    /**
     * Uploads host texel data into the selected image region.
     *
     * For example, upload one decoded sprite into a cell of an atlas without replacing the
     * surrounding sprites. [destination] selects its mip, aspect, layers, and texel box;
     * [sourceLayout] describes rows at [sourceAddress], starting with that box's first texel.
     * Image offsets do not skip bytes in the source. The region does not inherit render area,
     * viewport, or scissor state. All unselected destination contents retain their validity.
     *
     * Before returning normally, the implementation has consumed or independently saved every
     * occupied source row. The caller can then modify or release the host storage. It must not
     * retain only an address into a temporary memory frame, even if GPU emission is deferred.
     * Only texel words are read: row/slice gaps and nonexistent trailing padding are not inputs.
     * The caller must provide readable storage for those words during the call and prevent
     * concurrent writes. A nonzero address does not establish that host memory is accessible.
     *
     * Data uses the selected format/aspect's encoding defined by [TextureDataLayout], including
     * stored sRGB bytes and separate depth/stencil planes. No scaling, decoding, mip generation,
     * or sample reconstruction occurs. The image must be single-sampled and permit
     * TransferDestination; Sampled and ColorAttachment are not prerequisites. An attachment
     * operand retains its original permissions and import restrictions rather than granting access.
     *
     * CPU pitches need not satisfy a particular native staging alignment: the backend may
     * repack its private upload storage, without changing which source words are consumed.
     * Native format/operation support still matters. Requests that cannot preserve these
     * semantics are rejected rather than silently expanded or converted.
     *
     * Record outside a logical render-pass callback. As with [writeBuffer], conflicting accesses
     * through this encoder are ordered with execution and memory dependencies: earlier reads
     * precede this overwrite and later reads observe it unless intervening work changes the data.
     * Other recordings, queues, and host access need their own synchronization contract.
     * Returning does not wait for GPU completion. Backend staging survives all GPU reads of it.
     *
     * [ImageRegion.validateUpload] provides public metadata checks. The implementation also checks
     * device identity, native support, physical aliases, import scopes, and storage lifetime.
     * Memoryless storage is never silently replaced by backed storage to make a transfer succeed.
     *
     * @throws IllegalArgumentException if the address is zero, an endpoint belongs to another
     * device, a required permission is missing, or the region/sample/layout requirements fail.
     * @throws UnsupportedOperationException if the backend cannot execute the requested transfer.
     * @throws IllegalStateException if recording, resource, or access scope is invalid, or a
     * logical render pass is active.
     * @see <a href="https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdCopyBufferToImage.html">Vulkan staging-to-image copy</a>
     * @see <a href="https://registry.khronos.org/OpenGL/extensions/ARB/ARB_pixel_buffer_object.txt">OpenGL pixel transfer sources</a>
     */
    fun writeTexture(destination: ImageRegion, sourceAddress: NativeAddress, sourceLayout: TextureDataLayout = TextureDataLayout.TightlyPacked)

    /**
     * Copies linearly arranged texels from a GPU buffer into an image region.
     *
     * Use this when the source already resides in a staging or shader-produced buffer. Unlike
     * [writeTexture], source texels are read when this command executes, not captured during
     * the Kotlin call. There is no implicit CPU readback. The source view's byte zero locates
     * the first texel; its capacity must contain the complete [sourceLayout] footprint.
     * Extra capacity, row gaps, and slice gaps are not read. Source texel words must be defined.
     *
     * The source needs TransferSource and the destination TransferDestination. The image is
     * single-sampled. Encoding, selection, command ordering, and recording scope follow
     * [writeTexture]; only the source and its read time differ. Actual source and destination
     * storage must not overlap, even if represented by different resource kinds.
     *
     * Explicit GPU buffer pitches and offsets must be executable on this backend. Unlike a
     * CPU upload, repacking may require extra GPU work; it must not modify the source, widen the
     * accessed words, or read back to the CPU. Reject when no semantics-preserving path exists.
     * [ImageRegion.validateCopyFromBuffer] checks public usages, capacity, layout, and sample count,
     * not device support, physical aliasing, content validity, or access scopes.
     *
     * @throws IllegalArgumentException if resources belong to another device, required usages
     * are missing, metadata requirements fail, or the actual source/destination storage overlaps.
     * @throws UnsupportedOperationException if the backend cannot execute this transfer.
     * @throws IllegalStateException if recording, resources, source contents, or access scopes are
     * invalid, or a logical render pass is active.
     * @see <a href="https://docs.vulkan.org/refpages/latest/refpages/source/VkBufferImageCopy.html">Vulkan buffer/image addressing parameters</a>
     */
    fun copyBufferToTexture(source: GpuBufferView, destination: ImageRegion, sourceLayout: TextureDataLayout = TextureDataLayout.TightlyPacked)

    /**
     * Copies selected image texels into rows of a GPU buffer.
     *
     * This produces linear data for a later GPU operation or CPU readback. It does not map the
     * buffer, wait for the GPU, or make CPU access immediately safe. Completion and host
     * visibility must be established through the separate readback/access mechanism.
     *
     * The first output texel is at the destination view's byte zero. Only occupied rows in
     * [destinationLayout] are written; gaps and extra capacity retain both contents and validity.
     * Unused bits inside a 24-bit depth word follow [TextureDataLayout], not the gap rule.
     * Required capacity ends at the last texel, without trailing row or slice padding.
     *
     * The source needs TransferSource and the destination TransferDestination. The image must
     * be single-sampled; explicitly resolve MSAA color before this command when one value per
     * pixel is desired. Depth input must represent finite values in [0, 1]. The selected source
     * remains unchanged and every texel read must be defined. Actual storage must not overlap.
     *
     * Format/aspect encoding, scope, and ordering follow [writeTexture]. Explicit buffer pitches
     * have the execution requirements described by [copyBufferToTexture]. In particular, a
     * padded private readback followed by a buffer copy must copy occupied rows, not overwrite
     * the caller's gaps. [ImageRegion.validateCopyToBuffer] supplies public metadata checks;
     * the backend additionally checks native support, device identity, aliases, and access scopes.
     *
     * @throws IllegalArgumentException if resources belong to another device, usages are missing,
     * metadata requirements fail, or actual source/destination storage overlaps.
     * @throws UnsupportedOperationException if the backend cannot execute this transfer.
     * @throws IllegalStateException if recording, resources, source contents, or access scopes are
     * invalid, or a logical render pass is active.
     * @see <a href="https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdCopyImageToBuffer.html">Vulkan image-to-buffer copy</a>
     * @see <a href="https://learn.microsoft.com/en-us/windows/win32/direct3d12/readback-data-using-heaps">Direct3D 12 readback completion</a>
     */
    fun copyTextureToBuffer(source: ImageRegion, destination: GpuBufferView, destinationLayout: TextureDataLayout = TextureDataLayout.TightlyPacked)

    /**
     * Copies image contents to a matching region, preserving their formatted representation.
     *
     * For example, move a sprite between two atlas cells or copy a rendered result for a later
     * stage. Both regions have the same format, selected aspect, extents, layer count, and sample
     * count. Origins and mip levels can differ. Pair x/y/z positions and array layers in selection
     * order; this does not reinterpret volume slices as array layers or generate other mip levels.
     *
     * This is not a blit or resolve: there is no filtering, scaling, color-space conversion, or
     * reduction of samples. Equal multisample counts copy the original samples when supported.
     * A 4-sample to 1-sample request instead needs [resolve]. Linear buffers are not involved,
     * so their row strides and one-sample representation do not constrain this operation.
     *
     * The source needs TransferSource, the destination TransferDestination. The selected source
     * stays unchanged; unselected destination texels, layers, mips, and aspects are preserved.
     * All source texels must be defined; copied depth values must be finite and in [0, 1].
     * Actual source and destination contents must not overlap. Nonoverlapping cells of one image
     * are representable, but copying onto itself is rejected rather than given memmove semantics.
     *
     * Source data is read at this command's execution position, without a host snapshot or wait.
     * Recording scope, conflict ordering, and import/storage-lifetime checks follow [writeTexture].
     * [ImageRegion.validateCopyTo] checks public metadata and known overlap. A backend must still
     * resolve physical aliases and native restrictions; identical formats do not guarantee that
     * every partial depth, multisample, or cross-dimensional native copy is supported.
     *
     * @throws IllegalArgumentException if resources belong to another device, usages are missing,
     * the regions do not match for copying, or their actual source/destination contents overlap.
     * @throws UnsupportedOperationException if the backend cannot execute this copy.
     * @throws IllegalStateException if recording, resources, source contents, or access scopes are
     * invalid, or a logical render pass is active.
     * @see <a href="https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdCopyImage.html">Vulkan image copy</a>
     * @see <a href="https://registry.khronos.org/OpenGL/extensions/ARB/ARB_copy_image.txt">OpenGL raw image copy</a>
     * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/d3d12/nf-d3d12-id3d12graphicscommandlist-copytextureregion">Direct3D 12 texture copy</a>
     */
    fun copyTexture(source: ImageRegion, destination: ImageRegion)

    /**
     * Records drawing into the attachments selected by [description].
     *
     * [commands] runs once, using a [RenderPass] that is valid only during this callback.
     * Each attachment position selects the aspect affected by its load and store operations.
     * The device validates the attachment combination before accepting the pass for native
     * execution; each draw must also be compatible with its pipeline and bindings.
     *
     * Ending the callback ends the logical pass, not necessarily a native rendering scope.
     * The backend may jointly record and emit the pass and a subsequent resolve when this
     * preserves their recorded behavior. Contents needed by later operations must still be logically preserved;
     * combining native operations does not restore contents discarded at the logical pass boundary.
     *
     * Deferring native emission must preserve inputs supplied while recording, including data
     * provided through temporary host storage.
     *
     * @throws IllegalArgumentException if the description or resources are incompatible.
     * @throws UnsupportedOperationException if the device cannot execute the requested combination.
     * @throws IllegalStateException if a resource, content scope, or recording scope is invalid,
     * or another render pass is active on this encoder.
     */
    fun renderPass(description: RenderPassDescription, commands: RenderPass.() -> Unit)

    /**
     * Resolves each source pixel's color samples into one destination pixel for later
     * single-sampled processing, without changing the image's spatial resolution.
     *
     * At a geometry edge, samples can contain both foreground and background colors. Resolve
     * derives one pixel value from them. Four-sample scene color can then become input to bloom
     * or tone mapping without retaining four values per pixel in the result.
     *
     * Both regions select Color, use the same format, texel extents, and layer count, and must not
     * overlap in storage. The source is multisampled and the destination single-sampled. Layers
     * are paired in selection order. Complete image dimensions may differ when the explicitly
     * selected rectangles match. There is no scaling, format conversion, or implicit final output.
     *
     * Only the selected destination contents are replaced. Unselected pixels and subresources
     * keep their previous values and validity. The source is neither modified nor discarded;
     * later writes to it do not update this result without another resolve.
     *
     * Record outside a logical render-pass callback after producing the source contents. Every
     * sample read must still be defined, including pixels not written by the preceding pass.
     * Preserve needed contents with logical Store, not Discard. These regions do not inherit the
     * preceding pass's RenderArea, viewport, or scissor.
     *
     * Texture sources require ResolveSource and destinations require ResolveDestination. Neither
     * needs Sampled or ColorAttachment solely for resolve. View and attachment representations
     * cannot bypass the underlying storage's permissions. For opaque host attachments, the device
     * verifies resolve access against the import contract.
     *
     * This independent logical operation does not require a separate native render pass. A backend
     * may record and combine it with its producer when the regions and accesses permit that path.
     * Memoryless input must be consumed within a supported native lifetime. An unsupported sequence
     * is rejected, not changed to backed storage or repaired by reading logically discarded data.
     * The backend needs resolve information before issuing any native command that requires it;
     * postponing end-rendering alone cannot repair an already-issued begin configuration.
     *
     * This uses the implementation's standard color resolve, not a caller-defined filter. Sample
     * weighting and rounding are implementation-dependent; combining sRGB samples in linear color
     * space is not guaranteed. Selecting a native path must respect these guarantees.
     *
     * [ImageRegion.validateResolveTo] supplies metadata checks. The implementation must additionally
     * check device identity, all source access scopes, actual permissions, aliasing, native support,
     * and execution dependencies. Reference inequality does not prove disjoint storage.
     *
     * @throws IllegalArgumentException if an endpoint belongs to another device, lacks the required
     * permission, violates the region/format/sample requirements, or overlaps the other endpoint.
     * @throws UnsupportedOperationException if the device cannot execute this resolve for the
     * resources, regions, or required storage lifetime.
     * @throws IllegalStateException if recording is inactive, a logical render pass is active,
     * an access scope is invalid, or the source contents are no longer available.
     *
     * @see <a href="https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdResolveImage.html">Vulkan image resolve</a>
     * @see <a href="https://registry.khronos.org/OpenGL/extensions/ARB/ARB_framebuffer_object.txt">OpenGL framebuffer resolve</a>
     */
    fun resolve(source: ImageRegion, destination: ImageRegion)

    /**
     * Resolves the complete exposed color images of two attachments, including all selected layers.
     *
     * This keeps the attachment-based call form for host outputs. It uses the same permission and
     * scope checks as explicit regions, without assuming that an attachment is also a texture view.
     */
    fun resolve(source: RenderAttachment, destination: RenderAttachment) {
        resolve(
            ImageRegion.Attachment(source, arrayLayerCount = source.arrayLayerCount),
            ImageRegion.Attachment(destination, arrayLayerCount = destination.arrayLayerCount),
        )
    }

    /**
     * Gives up the current contents of [region] after their final use.
     *
     * A scene pass preserves its MSAA samples until [resolve] reads them. Discarding the source
     * afterward allows the backend to avoid retaining samples with no remaining consumers. A
     * Discard store on the producing pass itself would be too early for that logical sequence.
     *
     * From this command position, all samples of the selected aspect, mip, layers, and texel box
     * have undefined values. A later clear or overwrite establishes new contents; a partial write
     * leaves unwritten values undefined. Already undefined contents can be discarded again.
     * Other pixels, aspects, and subresources retain their previous values and validity.
     *
     * The declaration affects contents, not only the wrapper: aliases cannot recover old values.
     * A depth-only view or attachment cannot discard stencil merely because its storage contains it.
     * The operand must expose the selected aspect and range.
     *
     * No sampled, attachment, or transfer usage is required solely for this declaration. The device
     * must still recognize the source and admit the operation in its current access scope. When
     * native invalidation cannot express exactly this selection, it may be omitted; the backend
     * must not widen it and destroy data outside the requested region.
     *
     * Discard does not clear, release storage, wait for completion, cancel commands, or remove
     * dependencies. A later write must not race an earlier resolve read. A late discard cannot undo
     * a store that already executed. It permits optimization without promising a performance gain.
     *
     * Record outside a logical render pass. The selection is independent of earlier RenderArea,
     * viewport, and scissor state. No matching reset is needed: only new writes establish new data.
     *
     * @throws IllegalArgumentException if the source is not recognized by this device or the
     * operand selects data outside the exposed range.
     * @throws IllegalStateException if recording is inactive, a logical render pass is active,
     * or the source resource or access scope is invalid.
     *
     * @see <a href="https://registry.khronos.org/OpenGL/extensions/ARB/ARB_invalidate_subdata.txt">OpenGL invalidation</a>
     */
    fun discardContents(region: ImageRegion)

    /**
     * Discards one exposed aspect of the attachment's whole rectangle and all selected layers.
     *
     * [aspect] must belong to the attachment's selection, not merely to its storage format.
     * Use [ImageRegion.Attachment] to select a smaller rectangle or fewer layers.
     */
    fun discardContents(attachment: RenderAttachment, aspect: TextureAspect) {
        discardContents(ImageRegion.Attachment(attachment, aspect = aspect, arrayLayerCount = attachment.arrayLayerCount))
    }
}

/**
 * Fills temporary host storage and uploads it after [write] returns normally.
 *
 * This is useful for small per-draw or per-pass parameters: construct their bytes directly in
 * a [MemoryFrame], without keeping a separate host allocation or pairing map/unmap calls.
 * The temporary storage is not a mapping of [destination] and does not contain its old bytes.
 * Initialize the entire selected byte count, including padding; no automatic packing or
 * initialization is performed. The shader's data-layout rules still determine the field offsets.
 * The writer must run synchronously on the recording thread; its frame and address must not escape.
 *
 * [alignment] applies to the temporary host address, not to the destination buffer offset or
 * the shader binding. It must be a positive power of two. Byte alignment is sufficient for a
 * raw copy; request more when the host writer requires it.
 *
 * The helper uses the encoder's fixed-capacity [MemoryStack]. The byte count must fit an Int,
 * and the allocation, including alignment padding, must fit the remaining stack capacity. A
 * larger upload can use the address-based overload with separately supplied host storage.
 *
 * Once allocation and metadata checks succeed, [write] runs exactly once, including for an
 * empty destination. If it throws or returns nonlocally, this helper does not issue its upload.
 * Commands explicitly recorded by the callback itself are not rolled back. The frame is restored
 * on every exit, including failure of the upload call; no manual release or reset is required.
 * A normal callback return is followed by the address-based upload before the frame ends, so a
 * conforming implementation has saved the source bytes before the temporary address expires.
 *
 * @throws IllegalArgumentException if the byte count exceeds Int.MAX_VALUE, [alignment] is
 * invalid, or the destination lacks TransferDestination usage.
 * @throws IllegalStateException if the temporary allocation exceeds the remaining stack capacity.
 */
inline fun CommandEncoder.writeBuffer(destination: GpuBufferView, alignment: Int = 1, write: MemoryFrame.(address: NativeAddress) -> Unit) {
    require(destination.sizeBytes <= Int.MAX_VALUE.toLong()) { "Scoped buffer upload size exceeds Int.MAX_VALUE; use the address-based overload" }
    memoryStack.frame {
        val address = reserve(destination.sizeBytes.toInt(), alignment)
        destination.validateUpload(address)
        write(address)
        writeBuffer(destination, address)
    }
}

/**
 * Fills a temporary linear image and uploads its occupied rows after [write] returns normally.
 *
 * Use this for small generated images or atlas updates. The memory is not a mapping of the
 * texture and contains none of its old texels. Initialize every selected texel word in the
 * encoding defined by [TextureDataLayout]; padding between rows or slices need not be written.
 * [TextureDataLayout.footprintFor] gives row offsets for a writer using explicit strides.
 *
 * The enclosing span must fit an Int and the remaining [MemoryStack] capacity. [alignment]
 * is a positive power-of-two alignment for the temporary host address, not a GPU pitch or
 * binding alignment. Large uploads can use the address-based overload with other host storage.
 *
 * After allocation and metadata checks, the writer executes once on the recording thread.
 * If it throws or returns nonlocally, this helper issues no upload. Commands explicitly recorded
 * by the writer are not rolled back. The frame is restored on every exit, including upload failure;
 * neither the frame nor its address may escape the synchronous callback. On normal return the
 * address-based upload consumes the selected words before the frame ends. No unmap/reset is needed.
 *
 * @throws IllegalArgumentException if the span does not fit an Int, alignment is invalid, or
 * public destination/layout requirements fail.
 * @throws IllegalStateException if the temporary allocation cannot fit in the memory stack.
 */
inline fun CommandEncoder.writeTexture(destination: ImageRegion, sourceLayout: TextureDataLayout = TextureDataLayout.TightlyPacked, alignment: Int = 1, write: MemoryFrame.(address: NativeAddress) -> Unit) {
    val footprint = sourceLayout.footprintFor(destination)
    require(footprint.requiredSizeBytes <= Int.MAX_VALUE.toLong()) { "Scoped texture upload exceeds Int.MAX_VALUE; use the address-based overload" }
    memoryStack.frame {
        val address = reserve(footprint.requiredSizeBytes.toInt(), alignment)
        destination.validateUpload(address, sourceLayout)
        write(address)
        writeTexture(destination, address, sourceLayout)
    }
}
