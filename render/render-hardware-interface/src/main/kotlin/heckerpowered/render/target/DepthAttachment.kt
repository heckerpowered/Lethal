/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.target

import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.texture.TextureFormat

/**
 * Exposes a depth/stencil attachment for reuse by compatible render targets.
 *
 * Reusing an attachment makes those targets access the same stored depth and stencil values rather
 * than copying them. This is commonly used when rendering an effect into a separate color target
 * while retaining the scene depth, so effect geometry remains occluded by surfaces already drawn
 * in the scene.
 */
interface DepthStencilAttachment {
    val width: Int
    val height: Int
    val format: TextureFormat
    val sampleCount: SampleCount
}