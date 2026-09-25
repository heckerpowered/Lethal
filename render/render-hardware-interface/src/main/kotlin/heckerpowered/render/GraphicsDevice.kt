/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.buffer.BufferDescription
import heckerpowered.render.buffer.GpuBuffer
import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.pipeline.PipelineLayoutCreationException
import heckerpowered.render.pipeline.PipelineLayoutDescription
import heckerpowered.render.pipeline.RenderPipeline
import heckerpowered.render.sampler.GpuSampler
import heckerpowered.render.sampler.SamplerDescription
import heckerpowered.render.shader.ShaderModule
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderStages
import heckerpowered.render.shader.ShaderStagesDescription
import heckerpowered.render.target.RenderAttachment
import heckerpowered.render.target.validateAttachmentView
import heckerpowered.render.texture.*

/**
 * Owns backend resources and encodes graphics commands.
 *
 * Shader source reaches the backend through [createShaderModule]. An OpenGL
 * implementation may ask the driver to compile GLSL, while a Vulkan
 * implementation may compile it to SPIR-V with shaderc.
 */
interface GraphicsDevice {
    val memoryStack: MemoryStack
    val primitives: BuiltInPrimitives

    /**
     * Creates a [ShaderModule] for the stage and entry point selected by [description].
     *
     * Source code is compiled during module creation. Precompiled shader code does not require source
     * compilation, but may still be validated or translated before it can be used by this graphics
     * device.
     *
     * Creation fails if the shader representation is unsupported or invalid, source compilation
     * fails, the selected entry point does not exist, or the entry point cannot be used for the
     * requested stage. Reported failures include shader diagnostics when they are available.
     *
     * Successful creation establishes only that the module is valid in isolation. Compatibility with
     * other shader stages and with a complete render pipeline is established separately.
     */
    fun createShaderModule(description: ShaderModuleDescription): ShaderModule

    /**
     * Establishes the shader-stage combination described by [description].
     *
     * The returned [ShaderStages] may be reused when creating multiple compatible render pipelines.
     * This operation does not create a render pipeline or select vertex input, rasterization,
     * depth-stencil, blending, or attachment state.
     *
     * Every referenced shader module must be open and must have been created by this graphics device.
     * No two modules may declare the same [ShaderModule.stage]. If these requirements are violated, or
     * this device cannot establish the requested combination, creation fails and no [ShaderStages] is
     * returned.
     *
     * Successful creation does not establish compatibility with a particular pipeline layout, vertex
     * input, attachment configuration, or other fixed-function state. Those requirements are
     * established separately during render-pipeline creation.
     */
    fun createShaderStages(description: ShaderStagesDescription): ShaderStages

    /**
     * Establishes the shader-resource interface described by [description].
     *
     * For example, scene and material set layouts can be combined with small per-draw push
     * constants, then reused by several render pipelines. Set and binding numbers, descriptor
     * array counts, stage visibility, resource requirements, and push-constant byte ranges keep
     * their declared meanings on every backend.
     *
     * The device checks the complete interface, including per-stage and aggregate resource
     * limits, texture-binding forms, storage access, and push-constant capacity. Unsupported
     * requirements are rejected rather than dropping bindings, reducing arrays, or changing
     * their shader-visible addresses. All declared slots participate, even before a shader is
     * supplied; later shader compilation may eliminate accesses without changing this contract.
     *
     * Descriptor-set layouts are ordinary immutable descriptions. Any native set-layout objects
     * or binding maps required here are managed as backend state of the established layout, not
     * returned as extra public resources. No actual descriptor set or bound image is created.
     *
     * This checks interface support, not a particular shader program. Pipeline creation checks
     * shader declarations against this interface, and resource binding checks actual resources.
     * Push-constant ranges reserve an interface; their values are supplied by later commands.
     *
     * @throws UnsupportedOperationException if the interface cannot be represented or exceeds
     * this device's limits.
     * @throws IllegalStateException if the device cannot currently create resources.
     * @throws PipelineLayoutCreationException if establishing supported backend state fails.
     */
    fun createPipelineLayout(description: PipelineLayoutDescription): PipelineLayout

    /**
     * Establishes the texture storage requested by [description].
     *
     * A material image can be created for upload and shader reading, while a scene-color image
     * can be created for drawing and later post-processing. The description supplies the shape,
     * format, sample count, storage mode, and permitted operations for that allocation.
     *
     * Successful creation establishes those requested properties on the returned [GpuTexture].
     * The complete combination must be supported; individually supported properties do not
     * guarantee that they can be used together. An implementation must not silently substitute
     * a different format, reduce dimensions or sample counts, drop usage roles, or replace the
     * requested storage mode. Native implementation flags do not grant additional RHI usages.
     *
     * If [TextureDescription.cubeCompatible] is `true`, cube compatibility must be established
     * and [GpuTexture.cubeCompatible] must report `true`. Otherwise creation fails. A `false`
     * request leaves this capability optional; the returned property reports the compatibility
     * actually established and exposed by this device. It does not grant additional usage roles.
     * Specific view requests are still validated separately when those views are created.
     *
     * This operation provides no initial image data, makes no zero-initialization guarantee,
     * and does not generate mip contents or a resolve target. Uploads, clears, and rendering
     * establish contents separately. It creates storage rather than importing an existing
     * host image; no texture view or render-pass attachment is returned by this call.
     *
     * Texture creation checks resource support, not future command sequences. Memoryless access
     * scopes, view ranges, and pipeline or attachment compatibility are checked when the
     * corresponding operations are established or used.
     *
     * @throws UnsupportedOperationException if this device cannot support the requested
     * combination of shape, format, sample count, storage, usage, and cube compatibility.
     * @throws IllegalStateException if the device cannot currently create resources.
     * @throws TextureCreationException if backend allocation or texture establishment fails.
     */
    fun createTexture(description: TextureDescription): GpuTexture

    /**
     * Exposes the region of [texture] selected by [description] through a texture view.
     *
     * For example, an environment image can have a cube view for directional sampling and a
     * single-face two-dimensional view for rendering into that face. Both access the existing
     * storage rather than separate images.
     *
     * The requested view dimension, aspects, and ranges are preserved exactly. A request that
     * extends past the source is rejected, not clamped. The view keeps the source format, sample
     * count, and storage mode, and cannot enable a usage absent from the source texture.
     *
     * [TextureViewDescription.validateFor] supplies the common metadata checks. This operation
     * additionally checks that the device recognizes the source, that it remains valid, and that
     * the requested view representation is supported. It does not require initialized contents
     * or Sampled usage merely to establish a view; individual uses require their corresponding
     * usages and compatible shader or attachment interfaces.
     *
     * View creation neither copies texels nor initializes contents, performs a resolve, or begins
     * rendering. Creating a view of memoryless storage preserves its existing access restrictions
     * rather than extending the lifetime of its contents.
     *
     * Backend state established for the view is managed together with the source texture's
     * representation and released through that representation's cleanup. The returned view has
     * no separate close obligation and becomes unusable when the source is invalidated. This
     * applies equally to native view objects and to views represented by backend-managed metadata.
     *
     * @throws IllegalArgumentException if the device does not recognize [texture], or the
     * selection is incompatible with the source dimension, ranges, aspects, sample count, or
     * established cube compatibility.
     * @throws UnsupportedOperationException if this device cannot establish the requested view
     * representation, including any required array or cube-array capability.
     * @throws IllegalStateException if the source is invalid or the device or an import access
     * restriction prevents creating the view now.
     * @throws TextureViewCreationException if backend view establishment fails.
     */
    fun createTextureView(texture: GpuTexture, description: TextureViewDescription): GpuTextureView

    /**
     * Establishes a reusable drawing configuration from shader stages, their resource interface,
     * and fixed-function state.
     *
     * Shader resources must fit the declared layout: set and binding addresses, descriptor
     * kinds and array requirements, stage visibility, image interfaces, permitted storage
     * access, and push-constant byte ranges must agree. A missing layout exposes no descriptors
     * or push constants; it does not request inference from the shader or disable these checks.
     *
     * The backend retains validated interface information for the selected shader entry points,
     * including which sets and parameter bytes are used. This lets draws require the actual
     * inputs rather than every slot reserved by a more general layout. Buffer member offsets,
     * strides, and shader-required ranges still come from the shader interface; a descriptor's
     * minimum byte size is not a substitute for that information.
     *
     * Creation also checks device support for the complete drawing configuration. Actual
     * resources and pass attachments are selected later and checked before their use. No
     * descriptor resources or parameter values are supplied by creating the pipeline.
     *
     * @throws IllegalArgumentException if shader interfaces and the declared layout disagree,
     * referenced resources belong to another device, or the configuration is inconsistent.
     * @throws UnsupportedOperationException if the device cannot implement the requested configuration.
     * @throws IllegalStateException if the device or a referenced resource is not currently usable.
     */
    fun createRenderPipeline(description: RenderPipelineDescription): RenderPipeline
    fun createBuffer(description: BufferDescription): GpuBuffer
    fun createSampler(description: SamplerDescription): GpuSampler

    /**
     * Issues an ordered sequence of uploads, drawing, and image operations through a scoped encoder.
     *
     * For example, a material upload in one call can be read by drawing in a later call on this
     * device's default execution stream. The backend establishes execution and memory dependencies
     * between conflicting accesses, including through aliases. Other devices, explicit streams,
     * and host-native accesses need their own synchronization contract.
     *
     * [commands] runs exactly once, synchronously on this device's recording thread. The supplied
     * encoder and its passes reject access after their callbacks exit. Nested encode calls on the
     * same device are rejected. The application does not pair this scope with end, submit, or close.
     *
     * Commands may reach the native API during the callback. A Vulkan backend can record directly
     * into a native command buffer; a context-based backend may already issue work for execution.
     * No backend-independent command list, replay step, or whole-sequence preparation is required.
     * Uploads and push constants still consume or save their host inputs during the individual
     * calls; direct encoding must not leave a dependency on an expired temporary address.
     *
     * Normal return means the sequence was accepted and arranged to progress without another
     * encode call. It does not mean GPU completion, CPU readback availability, or presentation.
     * Resource and device validation may fail during a command or when finalizing this scope.
     * A successful return cannot guarantee that asynchronous execution will succeed later.
     *
     * This scope is not a transaction. Exceptional exit ends encoder access but does not undo
     * commands already issued, application side effects, or resource creation. An implementation
     * may discard an entirely unsubmitted native recording, but callers must not depend on this
     * across backends. A failure escaping a render-pass callback invalidates this recording;
     * catching it inside [commands] does not make continued encoding or successful finalization
     * valid. An argument rejected before a command changes recording state need not invalidate it.
     *
     * The backend retains potentially in-use upload, descriptor, and command storage until actual
     * completion or a backend-specific teardown guarantee makes release safe. Callback exit is
     * not that guarantee. Recording failure is not by itself device loss; the device interprets
     * native failures and prevents unsafe reuse without treating every exception as permanent loss.
     * Shared host graphics state must be restored according to the interoperation contract.
     *
     * @throws IllegalStateException if called on the wrong device thread, another recording is
     * active, or the device or current recording cannot safely accept work.
     *
     * @see <a href="https://docs.vulkan.org/spec/latest/chapters/cmdbuffers.html">Vulkan native recording</a>
     * @see <a href="https://registry.khronos.org/OpenGL/extensions/ARB/ARB_sync.txt">OpenGL execution completion</a>
     */
    fun encode(label: String, commands: CommandEncoder.() -> Unit)

    /**
     * Resolves a description through this device's pipeline cache.
     *
     * A cached result must satisfy the same interface and configuration contract as
     * [createRenderPipeline]; caching is not an alternative path that skips validation.
     * The returned pipeline is borrowed from the device and must not be closed by the caller.
     */
    fun resolveRenderPipeline(description: RenderPipelineDescription): RenderPipeline

    /**
     * Resolves an immutable sampler description through this device's resource cache.
     *
     * The returned sampler is borrowed from the device and must not be closed by the caller.
     */
    fun resolveSampler(description: SamplerDescription): GpuSampler

    /**
     * Exposes the image region selected by [view] for use as a render-pass attachment.
     *
     * For example, a scene pass can draw into this region and a later post-processing pass can
     * read the same image through the original texture view when Sampled usage is enabled.
     * Attachment creation itself does not require shader-sampling access.
     *
     * The result preserves the source storage, view format, selected aspects, sample count, and
     * array layers. The view must select exactly one mip level; a broader selection is rejected
     * rather than narrowed. No image data is allocated, initialized, copied, or resolved.
     *
     * A one-dimensional view represents a row. Array views retain their layers, and cube views
     * retain all selected faces in array-layer order. A volume view is not implicitly reduced to
     * a slice. Backends apply [validateAttachmentView], then check their native representation
     * support. Color requires ColorAttachment usage; Depth or Stencil requires
     * DepthStencilAttachment usage. A missing usage is rejected, not enabled by conversion.
     *
     * The result and [view] need not be the same Kotlin object. An existing attachment
     * representation can be reused after the same validation; implementing RenderAttachment
     * alone does not bypass device recognition or source checks. The result is not required to
     * implement GpuTextureView. Both representations must remain associated with the same
     * underlying storage and selection for invalidation and alias tracking.
     *
     * Load and store operations are chosen by each pass. Device-specific depth/stencil pairing,
     * layered rendering, pipeline compatibility, and content availability are checked when used.
     * This operation neither enters a render pass nor establishes synchronization. Import access
     * restrictions remain in force, but ordinary creation need not occur inside a render pass.
     * Backend state established for this representation is managed with the source texture, as
     * for texture views; the returned interface introduces no separate close operation.
     *
     * @throws IllegalArgumentException if the device does not recognize the source, its metadata
     * or selection is inconsistent, it selects multiple mips, or required attachment usage is absent.
     * @throws UnsupportedOperationException if the view dimension, format, or selection cannot
     * be represented as an attachment by this device.
     * @throws IllegalStateException if the source is invalid or an import restriction prevents
     * establishing this representation now.
     * @throws TextureViewCreationException if backend attachment-view establishment fails.
     */
    fun createAttachmentView(view: GpuTextureView): RenderAttachment
}
