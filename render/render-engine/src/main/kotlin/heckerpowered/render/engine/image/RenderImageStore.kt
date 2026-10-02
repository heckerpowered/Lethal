/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.image

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.ImageRegion
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.lifetime
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.*
import heckerpowered.render.terminateOnFailure
import heckerpowered.render.withFailureCleanup

/**
 * Reuses named color images across effect invocations instead of allocating them for every stage.
 *
 * A name selects one current image. Repeating a request with the same size, format and
 * [AlphaQuantity] returns that image and preserves its contents; changing any of them allocates
 * a replacement. Different names
 * use distinct allocations, and entries are retained until replacement or lifetime closure rather
 * than evicted by age or capacity.
 *
 * Replaced allocations are retained until GPU completion permits their release. A replaced image
 * must not be submitted to later stages: the engine releases retired allocations after a successful
 * completion wait. The current image remains usable until it too is replaced or the lifetime closes.
 *
 * The supplied [lifetime] is the final-release boundary and must remain open for image requests,
 * including cache hits. Construction registers image cleanup there. Closing the lifetime releases
 * current and retired images without waiting, so GPU completion must be established first.
 * The supplied sampler is managed separately; this store cannot be closed independently.
 */
class RenderImageStore(
    private val device: GraphicsDevice,
    private val lifetime: ResourceLifetime,
    private val sampler: GpuSampler,
) {
    private class Entry(
        val size: ImageSize,
        val format: TextureFormat,
        val alphaQuantity: AlphaQuantity,
        val image: RenderImage,
        val lifetime: ResourceLifetime,
    )

    private val images = LinkedHashMap<String, Entry>()
    private val retired = ArrayList<Entry>()

    init {
        lifetime.register(AutoCloseable {
            terminateOnFailure {
                releaseRetired()
                images.values.forEach { it.lifetime.close() }
                images.clear()
            }
        })
    }

    fun image(name: String, size: ImageSize, format: TextureFormat, alphaQuantity: AlphaQuantity = AlphaQuantity.Coverage): RenderImage {
        lifetime.checkOpen()
        val previous = images[name]
        if (previous?.size == size && previous.format == format && previous.alphaQuantity == alphaQuantity) return previous.image
        return ResourceLifetime.build {
            val texture = device.createTexture(name, size.width, size.height, format, TextureUsage.Sampled, TextureUsage.ColorAttachment).lifetime(this)
            val view = device.createTextureView(texture, TextureViewDescription(name))
            val attachment = device.createAttachmentView(view)
            val image = RenderImage(view, attachment, size, sampler, alphaQuantity)
            val entry = Entry(size, format, alphaQuantity, image, this)
            withFailureCleanup({
                if (previous != null) retired += previous
                images[name] = entry
                entry.image
            }) {
                if (previous != null) retired.remove(previous)
                else if (images[name] === entry) images.remove(name)
            }
        }
    }

    internal fun find(attachment: RenderAttachment): RenderImage? =
        (images.values.asSequence() + retired.asSequence()).firstOrNull { it.image.attachment === attachment }?.image

    internal fun find(texture: GpuTexture): RenderImage? =
        (images.values.asSequence() + retired.asSequence()).firstOrNull { it.image.view.texture === texture }?.image

    internal fun find(region: ImageRegion): RenderImage? = when (region) {
        is ImageRegion.Attachment -> find(region.attachment)
        is ImageRegion.Texture -> find(region.texture)
        is ImageRegion.View -> find(region.view.texture)
    }

    /** Releases replaced allocations after completion of every GPU command that may access them. */
    internal fun releaseRetired() {
        retired.forEach { it.lifetime.close() }
        retired.clear()
    }
}
