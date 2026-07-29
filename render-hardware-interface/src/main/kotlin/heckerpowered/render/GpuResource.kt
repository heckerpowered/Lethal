/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

/**
 * Resource whose lifetime may own backend state.
 */
interface GpuResource : AutoCloseable

interface GpuBuffer : GpuResource {
    val sizeBytes: Int
    val usage: Set<BufferUsage>
}

interface GpuTexture : GpuResource {
    val width: Int
    val height: Int
    val format: TextureFormat
}

interface GpuSampler : GpuResource

interface RenderPipeline : GpuResource

/**
 * Non-owning view of a depth attachment whose lifetime is controlled by its render target.
 */
interface DepthAttachment {
    val width: Int
    val height: Int
}

interface RenderTarget : GpuResource {
    val width: Int
    val height: Int
    val hasDepthAttachment: Boolean
    val depthAttachment: DepthAttachment?
}

interface TextureRenderTarget : RenderTarget {
    val colorTexture: GpuTexture
}
