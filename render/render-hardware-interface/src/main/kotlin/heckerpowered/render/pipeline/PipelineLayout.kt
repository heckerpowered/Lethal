/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

import heckerpowered.render.GpuResource

/**
 * Defines the shader-visible resource interface of compatible pipelines.
 *
 * A pipeline layout describes how resources supplied by the application are addressed by shaders.
 * It consists of an ordered sequence of [DescriptorSetLayout]s together with an optional
 * [PushConstantLayout].
 *
 * Descriptor sets group bindable resources such as buffers, textures, and samplers into numbered
 * sets. The position of a [DescriptorSetLayout] in [PipelineLayoutDescription.descriptorSets]
 * determines its set index, while each descriptor-set layout defines the bindings available
 * within that set.
 *
 * For example, a pipeline layout might expose:
 *
 * ```
 * set 0
 *   binding 0 -> camera uniform buffer
 *   binding 1 -> lighting uniform buffer
 *
 * set 1
 *   binding 0 -> material texture
 *   binding 1 -> material sampler
 *
 * push constants
 *   object index
 *   draw flags
 * ```
 *
 * A shader referring to `(set = 1, binding = 0)` therefore accesses the resource described by
 * binding 0 of the second descriptor-set layout.
 *
 * Push constants describe a small region of application-provided data that can be updated directly
 * while encoding commands without binding it through a descriptor set. They are useful for small,
 * frequently changing values associated with a draw or dispatch.
 *
 * The pipeline layout contains no shader code and does not describe fixed-function pipeline state.
 * Instead, it defines the resource-binding contract against which shaders and bound resources must
 * be compatible.
 *
 * The native representation is backend-specific. Vulkan implementations may map this resource to
 * a native pipeline layout, while other backends may retain or translate the same logical resource
 * interface without requiring a corresponding native object.
 */
interface PipelineLayout : GpuResource