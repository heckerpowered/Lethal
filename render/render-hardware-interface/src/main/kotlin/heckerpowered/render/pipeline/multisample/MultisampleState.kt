/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.multisample

/**
 * Describes how rasterization represents coverage within each framebuffer pixel.
 *
 * With multisampling, a pixel contains several sample locations rather than only one. A primitive
 * may cover some of those samples without covering the others, allowing polygon and line edges to
 * represent partial pixel coverage instead of abruptly treating the complete pixel as either
 * covered or uncovered.
 *
 * Typical uses include smoothing the visible edges of ordinary 3D geometry, preserving thin or
 * subpixel-sized triangles and lines more accurately, and—when [alphaToCoverageEnabled] is
 * enabled—smoothing alpha-tested surfaces such as leaves, grass, fences, and hair cards.
 *
 * Multisampling primarily improves edges produced by rasterization. It does not generally remove
 * aliasing caused by texture minification, high-frequency shader calculations, specular highlights,
 * or motion between frames. Those problems may require texture filtering, shader-specific
 * techniques, or temporal anti-aliasing.
 *
 * Increasing [sampleCount] also increases the storage and bandwidth required by multisampled color,
 * depth, and stencil attachments. A multisampled attachment is commonly resolved into a
 * single-sampled image before presentation or later sampling, but that resolve operation belongs to
 * the render pass rather than to this state.
 *
 * Multisampling does not by itself require one fragment-shader invocation for every sample. Explicit
 * per-sample shading is a separate capability.
 */
data class MultisampleState(
    /**
     * Number of rasterization samples associated with each framebuffer pixel.
     *
     * Higher counts can describe partial coverage more precisely, improving the appearance of
     * geometry edges at the cost of additional attachment storage and bandwidth.
     *
     * Support depends on both the graphics device and the formats of the attachments used with the
     * pipeline.
     */
    val sampleCount: SampleCount = SampleCount.One,

    /**
     * Converts the alpha component of the first fragment color output into a sample coverage mask.
     *
     * An alpha value near zero normally covers few or no samples, while an alpha value near one
     * covers most or all samples. Intermediate alpha values cover a corresponding portion of the
     * available samples. The exact sample pattern is implementation-dependent.
     *
     * This is useful for textures that represent sharply cut-out surfaces such as leaves, grass,
     * chain-link fences, and hair cards. Instead of discarding every sample at the same alpha
     * threshold, the edge can cover only part of a pixel and become smoother when the multisampled
     * attachment is resolved.
     *
     * Alpha-to-coverage is not alpha blending. It does not combine the fragment color with the
     * existing framebuffer color. Covered samples continue through the usual depth, stencil, and
     * color operations, while uncovered samples are not written.
     *
     * This option is primarily useful when [sampleCount] is greater than [SampleCount.One]. With
     * only one sample, the coverage mask contains only one bit and therefore cannot represent
     * several intermediate coverage levels.
     */
    val alphaToCoverageEnabled: Boolean = false,
)