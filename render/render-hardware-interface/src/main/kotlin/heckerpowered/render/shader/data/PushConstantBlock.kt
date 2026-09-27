/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader.data

import heckerpowered.render.shader.ShaderStage

/**
 * Describes a small parameter block filled by name and sent through a push-constant command.
 *
 * The generated writer uses std430 member alignment for the supported 32-bit scalars, vectors,
 * and column-major mat4. Float/Int properties and the field forms accepted by [GpuBufferData]
 * describe the bytes; [heckerpowered.render.Float2], [heckerpowered.render.Float3], and [heckerpowered.render.Float4] are also accepted as vector schema markers.
 * No runtime vector object is created for these markers.
 *
 * ExampleRange exposes the block at [offsetBytes] to [stages], and ExampleLayout contains that
 * range. Install the range in the pipeline interface separately. RenderPass.pushExample fills
 * temporary frame memory and pushes it only after the writer returns normally. Neither the
 * annotation nor the writer binds a pipeline or silently adds a range to an existing layout.
 *
 * The destination offset must be non-negative, four-byte aligned, and preserve every member's
 * shader alignment. Stages must be nonempty and unique. Unsupported field shapes, inheritance,
 * private or nested schemas, and custom property getters are diagnosed during generation.
 * Only padding is initialized automatically; the writer must initialize all actual fields.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PushConstantBlock(vararg val stages: ShaderStage, val offsetBytes: Int = 0)
