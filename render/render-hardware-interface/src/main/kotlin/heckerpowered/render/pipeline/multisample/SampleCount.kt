/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.multisample

/**
 * Defines the number of rasterization samples associated with each framebuffer pixel.
 *
 * The value specifies only the sample count. It does not prescribe the exact positions of samples
 * within a pixel; those positions are selected by the graphics implementation.
 *
 * Listing a count here does not guarantee that every device or attachment format supports it.
 */
enum class SampleCount(val value: Int) {
    /**
     * Uses one sample per pixel.
     *
     * This is ordinary single-sampled rendering and does not provide multisample anti-aliasing. It
     * is commonly used for post-processing passes, user-interface rendering, or pipelines whose
     * output is already anti-aliased by another technique.
     */
    One(1),

    /**
     * Uses two samples per pixel.
     *
     * This provides a modest improvement to geometry edges with a smaller attachment-storage and
     * bandwidth cost than higher sample counts. It can be useful when four samples are too
     * expensive for the target device or render targets.
     */
    Two(2),

    /**
     * Uses four samples per pixel.
     *
     * This is a common quality and cost balance for real-time 3D rendering. Four coverage samples
     * can represent geometry and alpha-to-coverage edges substantially more smoothly than a single
     * sample without the cost of higher sample counts.
     */
    Four(4),

    /**
     * Uses eight samples per pixel.
     *
     * This can further smooth thin geometry and shallow diagonal edges, but requires more attachment
     * storage and bandwidth and commonly provides diminishing visual improvement compared with four
     * samples.
     */
    Eight(8),
}