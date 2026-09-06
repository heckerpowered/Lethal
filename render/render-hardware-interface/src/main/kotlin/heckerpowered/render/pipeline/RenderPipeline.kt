/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

import heckerpowered.render.GpuResource

/**
 * A reusable drawing configuration established by a graphics device.
 *
 * A pipeline combines shader stages and their resource interface with the state needed to
 * interpret vertex input, assemble primitives, rasterize them, process depth and stencil, and
 * combine fragment outputs with render attachments.
 *
 * The shader stages alone do not determine all of that behavior. For example, opaque and
 * translucent draws may use the same shaders but different blending and depth-write settings,
 * and therefore use different pipelines.
 *
 * A pipeline describes how a draw processes its inputs and what attachment configuration it
 * requires. The actual buffers, textures, samplers, and render attachments are supplied
 * separately through bindings and the render pass. Compatible draws can reuse the pipeline
 * while changing those resources.
 *
 * The configuration remains unchanged after creation.
 */
interface RenderPipeline : GpuResource
