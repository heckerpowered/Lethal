/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.BuiltInPrimitives
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.binding.DescriptorSet
import heckerpowered.render.buffer.GpuBuffer
import heckerpowered.render.memory.*
import heckerpowered.render.pipeline.PipelineLayoutDescription
import heckerpowered.render.pipeline.PushConstantLayout
import heckerpowered.render.pipeline.RenderPipeline
import heckerpowered.render.pipeline.depthstencil.StencilOperation
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
     * selections and does not rewrite push-constant values or reset the stencil reference. Each
     * draw checks whether its retained inputs are valid for the newly selected configuration;
     * the backend re-establishes native bindings when required.
     *
     * The pipeline must be recognized by this device and remain usable. Its compatibility with
     * the pass's attachments and current inputs must be checked before a consuming draw.
     */
    fun bindPipeline(pipeline: RenderPipeline)
    fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Int = 0)

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
     * Draws [vertexCount] consecutive vertices starting at [firstVertex] with the selected state.
     *
     * A pipeline must be selected. Before accepting the draw for native execution, the backend
     * checks its pass attachments, required descriptor sets and shader-specific resource
     * accesses, and the values of push-constant bytes read by its selected shader entry points.
     * A layout position unused by those shaders does not require a descriptor selection.
     *
     * The draw retains the resource selections and parameter values active at this recording
     * position. Later binds and parameter updates affect later draws, not this one. This does
     * not snapshot buffer or image contents: their values still follow recorded writes and
     * synchronization. A zero count emits no primitives; it does not bypass state validation.
     *
     * @throws IllegalArgumentException if either count or index is negative, or supplied
     * resources, layouts, or attachments contradict the pipeline's requirements.
     * @throws UnsupportedOperationException if the backend cannot execute the requested combination.
     * @throws IllegalStateException if the pass or a resource is invalid, no pipeline is selected,
     * a required set is unbound, or required parameter values have not been established compatibly.
     */
    fun draw(vertexCount: Int, firstVertex: Int = 0)

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