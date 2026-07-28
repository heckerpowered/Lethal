/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

/**
 * Declares one typed push-constant block.
 *
 * Properties use scalar Kotlin types or shader-schema vector types such as [Float2], [Float3], and [Float4]. The generated API
 * provides a setter-based writer, records the block at [offsetBytes], and contributes its fields to the matching
 * pipeline layout. [stages] describes every field in the block. Both the offset and generated structure size must be
 * four-byte aligned as required by Vulkan push constants.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PushConstantBlock(vararg val stages: ShaderStage, val offsetBytes: Int = 0)
