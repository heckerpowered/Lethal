/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.texture

/**
 * Determines whether a texture keeps its image data for later use or holds only temporary
 * rendering data.
 *
 * A texture normally behaves like a stored image: a picture can be uploaded once and used by
 * many draws, or a rendered image can be kept as input to a later operation. [Backed] provides
 * this ordinary behavior and is the default storage mode.
 *
 * Some textures are needed only as working data while producing another image. For example,
 * rendering may need depth values to determine which surfaces are visible, even though only
 * the resulting color image is used afterward. [Memoryless] allows such intermediate data to
 * remain in temporary rendering storage instead of being kept as a separate image for later use.
 */
enum class TextureStorage {
    /**
     * Provides backing memory that can retain the texture's image data between uses.
     *
     * This is the normal choice for textures. Producing the data and consuming it do not have
     * to happen in the same rendering operation: a material image can be uploaded before it is
     * drawn, a rendered image can become input to post-processing, and a previous frame can be
     * retained for comparison with the next one.
     *
     * Backing storage makes this retention possible; it does not require every result to be
     * kept. When the texture is used as an attachment, its store operation must still preserve
     * any contents needed afterward. Explicitly discarding contents gives up those values even
     * though the backing memory still exists.
     */
    Backed,

    /**
     * Holds attachment data in temporary rendering storage rather than retaining it for later
     * independent use.
     *
     * This is useful when an image helps produce a result but is not itself needed afterward.
     * For example, depth testing uses stored depth values to decide which surfaces hide others
     * as a scene is drawn. If subsequent work needs only the resulting color image, preserving
     * the completed depth image would keep data that no later operation consumes.
     *
     * On supporting GPUs, rendering can process small regions of an image using temporary
     * on-chip storage. Keeping intermediate attachment data there can avoid allocating backing
     * storage for the complete intermediate image and avoid writing out data that will not be
     * used again.
     *
     * Multisampled color is another common example. Rendering produces several color samples
     * per pixel, while later post-processing may need only one resolved value per pixel:
     *
     * ```
     * render into multisampled color (Memoryless)
     *     -> resolve into single-sampled color (Backed)
     *     -> read the resolved image in later post-processing
     * ```
     *
     * The individual samples are needed until resolve consumes them, but need not be retained
     * afterward. Only the single-sampled result needs backing storage in this example.
     *
     * These contents must be produced and consumed within the native rendering scope that keeps
     * the temporary storage available. Declare a pass-local color resolve before rendering begins
     * through RenderPassDescription.colorResolves, and discard the original samples at that pass's
     * boundary. This lets a direct backend configure the destination without recording future draws.
     * Merely placing a standalone resolve after a pass does not promise fusion or prolong storage.
     * Ending the native scope first and resolving afterward cannot recover the source samples.
     * A separately selected planning facility may arrange a supported shared scope, but is not
     * implied by this storage mode.
     *
     * Reusing the texture object in another native rendering scope requires establishing its
     * contents again. It does not make values from the previous scope available.
     *
     * Choose [Backed] when later work must read the original image outside this scope, such as
     * sampling the depth image in a separate post-processing stage or retaining it for the next
     * frame. A texture being short-lived does not by itself make this mode suitable.
     */
    Memoryless,
}
