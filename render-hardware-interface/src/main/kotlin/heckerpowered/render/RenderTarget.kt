/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

enum class TextureFormat {
    RGBA8UNORM,
    RGBA16Float,
}

sealed class DepthAttachmentUsage {
    object None : DepthAttachmentUsage()
    object Create : DepthAttachmentUsage()

    data class Share(val attachment: DepthAttachment) : DepthAttachmentUsage()
}

data class RenderTargetDescription(
    val label: String,
    val width: Int,
    val height: Int,
    val colorFormat: TextureFormat,
    val depthAttachment: DepthAttachmentUsage = DepthAttachmentUsage.None,
) {
    init {
        require(width > 0 && height > 0) { "Render target dimensions must be positive" }
    }
}

enum class AttachmentLoadOperation {
    Load,
    Clear,
    Discard,
}

enum class AttachmentStoreOperation {
    Store,
    Discard,
}

data class Color(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
) {
    companion object {
        val TransparentBlack = Color(0.0F, 0.0F, 0.0F, 0.0F)
    }
}

data class RenderPassDescription(
    val label: String,
    val target: RenderTarget,
    val colorLoadOperation: AttachmentLoadOperation = AttachmentLoadOperation.Load,
    val colorStoreOperation: AttachmentStoreOperation = AttachmentStoreOperation.Store,
    val clearColor: Color = Color.TransparentBlack,
    val depthLoadOperation: AttachmentLoadOperation = AttachmentLoadOperation.Load,
    val depthStoreOperation: AttachmentStoreOperation = AttachmentStoreOperation.Store,
    val clearDepth: Double = 1.0,
) {
    init {
        require(clearDepth in 0.0..1.0) { "Depth clear value must be between zero and one" }
    }
}
