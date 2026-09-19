/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.texture

/**
 * Declares the operations a texture must support.
 *
 * Sampling an image, drawing into it, and writing it from a shader can require different device
 * capabilities and memory layouts. The device needs the intended uses before creation to prepare
 * suitable storage. Requesting every use can prevent optimizations, and some combinations cannot
 * be supported at all.
 *
 * For example, a texture rendered into and then sampled by post-processing needs both
 * [ColorAttachment] and [Sampled]. With only [ColorAttachment], drawing into the texture is
 * permitted, but reading it as a sampled shader input is not.
 *
 * These are requirements, not performance hints. If [GpuTexture.usage] does not include a use
 * required by an operation, the RHI must reject that operation. Creating a view or changing a
 * binding does not add a missing use. Request the needed uses when creating the texture.
 *
 * Declaring a use does not remove the format, sample-count, storage, or synchronization
 * requirements of an operation. These roles describe RHI operations, not native usage flags.
 */
enum class TextureUsage {
    /**
     * Allows shaders to read the texture through texture-input bindings.
     *
     * Typical uses include material images, shadow maps, lookup tables, and the input images of
     * post-processing effects. The shader chooses which texels to read rather than being limited
     * to the framebuffer location currently being processed.
     *
     * This includes both sampling with a sampler and fetching texels by integer coordinates where
     * the texture type supports them. Multisampled textures require multisample-aware fetches
     * with a sample index; they are not ordinary filtered textures.
     *
     * Filtering and comparison sampling have additional format and device requirements. This
     * role does not permit shader writes.
     */
    Sampled,

    /**
     * Allows shaders to access the texture through storage-image bindings.
     *
     * This is useful when a computation writes image data directly, such as updating a simulation
     * field or producing an image without drawing geometry. A later operation may read the result
     * through another storage binding or through [Sampled] access if that role is also declared.
     *
     * The binding and shader declarations determine whether an access reads, writes, or performs
     * an atomic operation. Atomic operations and supported access modes depend on the format and
     * device. Storage access does not use sampler filtering.
     */
    Storage,

    /**
     * Allows the texture to serve as a color attachment that directly receives drawing.
     *
     * Fragment outputs update the selected image region through the configured color-write and
     * blend state. Attachment clears and preservation of existing color contents are also part
     * of this role. The format must support color-attachment access, with blending supported
     * separately when it is enabled.
     *
     * A scene-color image read by later post-processing commonly also declares [Sampled]. A
     * multisampled image used as a resolve source additionally declares [ResolveSource]. Merely
     * receiving a resolve result requires [ResolveDestination], not this drawing role.
     */
    ColorAttachment,

    /**
     * Allows the texture to serve as a depth or stencil attachment during drawing.
     *
     * Depth values can select the nearest visible surface, while stencil values can mark or mask
     * regions for later drawing. The pass and depth-stencil state determine which available
     * aspects are read, cleared, updated, or preserved; the format determines which aspects exist.
     *
     * A depth-only texture does not acquire stencil data from this declaration, and declaring the
     * role does not enable either test. Reading the completed depth image through a later shader
     * texture binding additionally requires [Sampled].
     */
    DepthStencilAttachment,

    /**
     * Allows a fragment shader to read an attachment value at the framebuffer location it is
     * currently processing.
     *
     * For example, a lighting stage can consume G-buffer values produced earlier at the same
     * pixel. On supporting backends this local access can keep intermediate attachment data
     * within a rendering scope instead of making it an independently sampled image.
     *
     * Unlike [Sampled], this role does not provide arbitrary texture-coordinate lookup or sampler
     * filtering. A multisample-aware input may select a sample at the current location. The pass
     * structure and access dependencies must support the requested read; declaring this role does
     * not by itself make an attachment feedback loop legal.
     */
    InputAttachment,

    /**
     * Allows transfer operations to read the texture as the source of an image copy or a copy
     * into a buffer.
     *
     * This is useful for duplicating image regions or staging image data for CPU readback. The
     * copy operation defines compatible formats, ranges, and sample counts; this role does not
     * make the texture directly addressable by the CPU.
     *
     * Resolving multisampled contents requires [ResolveSource] instead of being implied by this
     * role. Declaring both permits both kinds of operation.
     */
    TransferSource,

    /**
     * Allows transfer operations to write the texture through image copies or data uploads.
     *
     * Typical uses include uploading a material image and receiving a copied image region. The
     * transfer operation determines the data representation and range being written. Clearing a
     * bound color or depth-stencil attachment belongs to its attachment role instead.
     *
     * This does not grant shader-write access or permission to receive a multisample resolve;
     * those uses are expressed by [Storage] and [ResolveDestination].
     */
    TransferDestination,

    /**
     * Allows a resolve operation to read the texture's multiple samples and produce a
     * single-sampled result in another texture.
     *
     * A scene can be drawn into multisampled color, then resolved for ordinary post-processing.
     * Depth and stencil resolves use the same source role where supported, but their available
     * modes differ from color resolves; resolving does not universally mean taking an average.
     *
     * The source must have more than one sample per pixel. The resolve operation selects its
     * range, aspect, destination, and mode. It does not implicitly discard the original samples.
     *
     * This role does not require [Sampled] or [TransferSource]. For [TextureStorage.Memoryless],
     * the backend must execute resolve while the source samples remain available within a
     * supported native rendering scope.
     */
    ResolveSource,

    /**
     * Allows a resolve operation to write its single-sampled result into this texture.
     *
     * The destination is a separate image region, not an alternate name for the multisampled
     * source. Later consumers choose this texture explicitly; for example, post-processing input
     * commonly combines this role with [Sampled].
     *
     * The destination must have one sample per pixel. Its format, selected aspect, and range must
     * satisfy the resolve operation's requirements. Receiving resolved data does not change the
     * destination's format, dimensions, or sample count.
     *
     * This role does not by itself permit ordinary drawing or transfer writes, even when the
     * backend uses a native attachment or transfer operation to produce the result.
     */
    ResolveDestination,
}
