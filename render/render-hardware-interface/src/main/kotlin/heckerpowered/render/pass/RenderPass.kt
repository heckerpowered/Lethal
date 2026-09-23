/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.BuiltInPrimitives
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.binding.DescriptorSet
import heckerpowered.render.buffer.GpuBuffer
import heckerpowered.render.buffer.GpuBufferView
import heckerpowered.render.memory.*
import heckerpowered.render.pipeline.PipelineLayoutDescription
import heckerpowered.render.pipeline.PushConstantLayout
import heckerpowered.render.pipeline.RenderPipeline
import heckerpowered.render.pipeline.depthstencil.StencilOperation
import heckerpowered.render.pipeline.primitive.IndexFormat
import heckerpowered.render.pipeline.vertex.VertexState
import heckerpowered.render.pipeline.vertex.VertexStepMode
import heckerpowered.render.shader.ShaderStage

/**
 * Records draws and the state they use within one logical render pass.
 *
 * [RenderPassDescription] selects the attachments and their load and store operations. Pipelines
 * and resource bindings can change between draws, allowing several objects with different
 * materials to contribute to the same attachments without beginning a new pass.
 *
 * This interface is supplied to the pass-recording callback and is valid only during that callback.
 */
interface RenderPass {
    val memoryStack: MemoryStack
    val primitives: BuiltInPrimitives

    /**
     * Selects the pipeline described by [description], using the device's pipeline resolution path.
     *
     * Establishing or reusing it must enforce the same shader-interface and device checks as
     * explicit pipeline creation. Its effects on recorded state are the same as binding the
     * resulting [RenderPipeline].
     */
    fun bindPipeline(description: RenderPipelineDescription)

    /**
     * Selects the drawing configuration used by subsequent draws and push-constant updates.
     *
     * The pass starts without a selected pipeline. Switching pipelines retains logical descriptor
     * selections, vertex-buffer selections, and the index binding. It does not rewrite
     * push-constant values or reset the stencil reference. Each
     * draw checks whether its retained inputs are valid for the newly selected configuration;
     * the backend re-establishes native bindings when required.
     *
     * The pipeline must be recognized by this device and remain usable. Its compatibility with
     * the pass's attachments and current inputs must be checked before a consuming draw.
     */
    fun bindPipeline(pipeline: RenderPipeline)

    /**
     * Selects a byte range that supplies vertex or instance attributes through [slot].
     *
     * The slot indexes [VertexState.buffers], not a shader attribute location. The selected
     * pipeline supplies stride, attribute formats, and stepping mode; this command supplies
     * the actual bytes. Element zero begins at [view]'s offset in its complete buffer.
     *
     * Bindings start empty and may be selected before a pipeline. Replacing a slot affects
     * subsequent draws only. Pipeline changes retain the logical selection and reinterpret it
     * using the new pipeline's layout; they do not restore a previously selected buffer.
     *
     * The range must be non-empty and permit `BufferUsage.Vertex`. The selected attributes must
     * fit inside the range when consumed; having more storage outside the view does not help.
     * Device alignment, resource validity, and native size limits are checked by the backend.
     *
     * @throws IllegalArgumentException if the slot, range, usage, or alignment is invalid.
     * @throws UnsupportedOperationException if the binding cannot be represented by the device.
     * @throws IllegalStateException if the pass or resource is unavailable.
     */
    fun bindVertexBuffer(slot: Int, view: GpuBufferView)

    /**
     * Binds the remainder of [buffer] starting at [offsetBytes].
     *
     * Use the view overload to give the binding a shorter explicit range. The byte offset is
     * measured from the complete buffer and is independent of the draw's element indices.
     */
    fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Size = 0) {
        require(offsetBytes >= 0 && offsetBytes <= buffer.sizeBytes) { "Vertex binding offset is outside the buffer" }
        bindVertexBuffer(slot, GpuBufferView(buffer, offsetBytes, buffer.sizeBytes - offsetBytes))
    }

    /**
     * Selects the indices used by subsequent [drawIndexed] commands.
     *
     * There is one index binding per pass, independent of numbered vertex-buffer slots. The
     * view selects the bytes and [format] determines how successive unsigned indices are read.
     * `firstIndex = 0` in a draw starts at the beginning of this view, not of the complete buffer.
     *
     * The binding starts empty, survives pipeline switches, and is ignored by [draw]. Rebinding
     * changes later indexed draws without changing already recorded selections or copying data.
     * The range must satisfy [IndexFormat.validateBinding]; native format support and resource
     * validity are additional checks. Binding does not scan index values.
     *
     * @throws IllegalArgumentException if index usage, alignment, or range size is invalid.
     * @throws UnsupportedOperationException if this format or binding cannot be represented.
     * @throws IllegalStateException if the pass or resource is unavailable.
     */
    fun bindIndexBuffer(view: GpuBufferView, format: IndexFormat)

    /**
     * Selects the actual resources supplied through one numbered descriptor set.
     *
     * For example, set 0 can supply camera data while set 1 supplies a material image and sampler.
     * Replacing set 1 changes the resources used by subsequent draws without replacing set 0 or
     * changing the pass attachments. Draws already recorded keep their earlier selections.
     *
     * [set] indexes the pipeline layout's descriptor-set list, not a binding within a set.
     * Before a draw uses this selection, [descriptors]' layout must equal the declaration at that
     * position and its resources must satisfy the shader's requirements. Separate but structurally
     * equal layout descriptions are accepted; reference identity is not required.
     *
     * Sets start unbound for each pass. Binding may precede pipeline selection; compatibility is
     * checked no later than the consuming draw. A draw requires the sets actually used by its
     * selected shader entry points, not every position reserved by the layout. Pipeline changes
     * retain logical selections, but a draw cannot consume one with an incompatible layout.
     * [PipelineLayoutDescription.validateDescriptorSets] checks this logical relationship when
     * supplied with the backend's validated set-use information. The backend must establish
     * the corresponding native bindings rather than relying on native state surviving the switch.
     *
     * The set fixes resource references, not their pixel or byte contents. Uploads, rendering, and
     * synchronization still determine the values read by the shader. Constructing a set does not
     * establish device support, resource validity, or initialized contents.
     *
     * @throws IllegalArgumentException if [set] is negative or a checked resource or layout is invalid.
     * @throws UnsupportedOperationException if the backend cannot establish the requested bindings.
     * @throws IllegalStateException if recording or a referenced resource is no longer valid.
     */
    fun bindDescriptorSet(set: Int, descriptors: DescriptorSet)
    /**
     * Supplies small parameter values for subsequent draws, such as an object's transform or color.
     *
     * A pipeline must already be selected and expose a push-constant layout. [destinationOffsetBytes]
     * is absolute within that interface, not relative to one range. The write must satisfy
     * [PushConstantLayout.validateWrite], including every stage that exposes the updated bytes.
     * The command captures [sizeBytes] of data at [sourceAddress] before returning so temporary
     * host memory can be reused without changing already recorded inputs.
     *
     * Values start undefined for this pass. Updating a subrange replaces only those bytes for
     * the named stages; other values keep their earlier write history. Before a draw reads a
     * value, it must have been supplied by a write with push-constant declarations compatible
     * with that draw's pipeline. Descriptor-set differences do not affect this comparison.
     *
     * Binding a different pipeline alone does not erase the values. Switching from pipeline A
     * to B and back to A can reuse A's values when no intervening write has replaced them. A
     * write under an incompatible layout cannot silently be reinterpreted under A; affected
     * bytes needed by A must be written again using a compatible layout.
     *
     * @throws IllegalArgumentException if the byte range or stage selection violates the layout.
     * @throws IllegalStateException if the pass is inactive, no pipeline is selected, or that
     * pipeline exposes no push constants.
     */
    fun pushConstants(stages: Set<ShaderStage>, sourceAddress: NativeAddress, sizeBytes: Int, destinationOffsetBytes: Int = 0)

    /**
     * Draws consecutive vertices, optionally repeating the geometry for several instances.
     *
     * A triangle list consumes each consecutive group of three vertices as one triangle.
     * For example, `draw(6, firstVertex = 12)` consumes vertex elements 12 through 17 and
     * describes two triangles. The pipeline's primitive topology decides how the sequence
     * is assembled; [vertexCount] is not a triangle or byte count.
     *
     * Each instance uses the same vertex interval. [VertexStepMode.Instance] streams instead
     * advance once per instance, beginning at [firstInstance]. Drawing a mesh three times can
     * therefore use one vertex stream and three different transforms from an instance stream.
     * [firstVertex] affects vertex-rate streams only; [firstInstance] affects instance-rate
     * streams only. Both are relative to the selected buffer views. Raw shader built-ins obey
     * the supplied shader representation's rules, which are not identical across backends.
     *
     * A pipeline must be selected. Before accepting the draw for native execution, the backend
     * checks its attachments, vertex inputs, required descriptor sets and shader-specific
     * resource accesses, and the push-constant values read by its selected entry points.
     * [VertexState.validateDrawInputs] provides the metadata-based vertex-input checks.
     *
     * The draw retains selections and parameter values from this recording position. Later
     * binds affect later draws; buffer and image contents still follow recorded writes and
     * synchronization. If either count is zero, no primitives or attribute reads are produced,
     * but argument, binding, and other state validation is not bypassed.
     *
     * @throws IllegalArgumentException if a count or first element is negative, a fetched range
     * is invalid, or resources, layouts, or attachments contradict the pipeline requirements.
     * @throws UnsupportedOperationException if the device cannot execute the requested combination.
     * @throws IllegalStateException if the pass or a resource is invalid, no pipeline is selected,
     * a required binding is missing, or required parameter values have not been established.
     *
     * @see [Vulkan vkCmdDraw](https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdDraw.html)
     * @see [OpenGL instanced draw parameters](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_base_instance.txt)
     * @see [D3D12 DrawInstanced](https://learn.microsoft.com/en-us/windows/win32/api/d3d12/nf-d3d12-id3d12graphicscommandlist-drawinstanced)
     */
    fun draw(vertexCount: Int, firstVertex: Int = 0, instanceCount: Int = 1, firstInstance: Int = 0)

    /**
     * Draws vertices selected by the bound index range, optionally for several instances.
     *
     * Four stored vertices A, B, C, and D can form a quad using six indices `0, 1, 2, 2, 3, 0`.
     * Shared vertices do not need duplicate attribute records. [indexCount] counts index entries
     * per instance, including restart markers, rather than distinct vertices or primitives.
     *
     * [firstIndex] selects the first index element within the index binding. For each ordinary
     * index, the draw adds the signed [baseVertex] and selects that element from each vertex-rate
     * stream. For example, local indices `0, 1, 2` with `baseVertex = 100` fetch elements 100, 101,
     * and 102. This relocates a mesh within a larger stream without editing its index data.
     * [baseVertex] may be negative, but every resulting vertex element must be non-negative,
     * representable without 32-bit unsigned wraparound, and valid for the bound streams.
     *
     * With primitive restart enabled, the stored [IndexFormat.restartIndex] ends the current
     * strip before [baseVertex] is added. The next index begins a fresh strip. Restart is
     * independent for each instance. Disabled restart leaves the maximum index usable as data.
     *
     * Instances use the same index sequence. Instance-rate buffers begin at [firstInstance]
     * and are unaffected by [firstIndex] and [baseVertex]. An index binding is required even
     * for an empty draw. The declared index range must fit the bound view; a zero index count
     * may start at its end. Either zero count produces no attribute reads or primitives, but
     * does not bypass the state checks described by [draw].
     *
     * [IndexFormat.validateDrawRange] checks the index bytes and returns their buffer-relative
     * start. [VertexState.validateIndexedDrawInputs] checks required streams and instance data.
     * Neither examines stored indices: the caller must ensure actual vertex fetches are valid.
     * Backends must not hide a GPU readback or wait inside this metadata validation.
     *
     * @throws IllegalArgumentException if an argument other than [baseVertex] is negative, a
     * checked range is invalid, or supplied state contradicts the pipeline requirements.
     * @throws UnsupportedOperationException if format, instancing, base offsets, or restart
     * cannot be executed with the requested semantics.
     * @throws IllegalStateException if the pass or a resource is invalid, required pipeline or
     * bindings are missing, or required parameter values have not been established.
     *
     * @see [Vulkan vkCmdDrawIndexed](https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdDrawIndexed.html)
     * @see [OpenGL base vertex and restart](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_draw_elements_base_vertex.txt)
     * @see [D3D12 DrawIndexedInstanced](https://learn.microsoft.com/en-us/windows/win32/api/d3d12/nf-d3d12-id3d12graphicscommandlist-drawindexedinstanced)
     */
    fun drawIndexed(indexCount: Int, firstIndex: Int = 0, baseVertex: Int = 0, instanceCount: Int = 1, firstInstance: Int = 0)

    /**
     * Sets the stencil reference value used by subsequent draws in this render pass.
     *
     * The reference is shared by front- and back-facing primitives. It initially equals zero,
     * remains active until changed again, and is not reset when another pipeline is bound.
     *
     * Stencil comparison masks the reference and stored stencil value independently using the
     * pipeline's read mask. [StencilOperation.Replace] instead uses the unmasked reference as its
     * operation result; the write mask then selects which result bits are stored.
     *
     * The value is ignored by pipelines that do not enable stencil testing.
     */
    fun setStencilReference(reference: UByte)
}

inline fun RenderPass.pushConstants(
    stages: Set<ShaderStage>,
    sizeBytes: Int,
    destinationOffsetBytes: Int = 0,
    write: MemoryFrame.(address: NativeAddress) -> Unit,
) {
    memoryStack.alloc(bytes(sizeBytes)) { address ->
        write(address)
        pushConstants(stages, address, sizeBytes, destinationOffsetBytes)
    }
}
