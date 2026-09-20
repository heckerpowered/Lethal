/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

import heckerpowered.render.GpuResource
import heckerpowered.render.binding.DescriptorSetLayout

/**
 * A shader-resource interface established by a graphics device for use by compatible pipelines.
 *
 * The layout gives shader resource addresses a stable meaning: a scene set can contain camera
 * buffers, a material set can contain textures and samplers, and push constants can supply a
 * small object index for each draw. Different actual resources can satisfy that same interface.
 *
 * [PipelineLayoutDescription.descriptorSets] assigns set numbers by list position. Each
 * [DescriptorSetLayout] assigns explicit binding numbers within its set. Push-constant offsets
 * instead refer to the byte ranges in [PushConstantLayout], outside the descriptor-set namespace.
 *
 * Creating this resource establishes support for the declared interface, not compatibility with
 * every shader. Pipeline creation must match the shader's resource types, counts, stages, and
 * data requirements to the layout. Actual resource bindings are checked separately when used.
 *
 * A layout contains no shader program, fixed-function drawing state, or currently bound resource
 * values. It may be reused by multiple pipelines. The backend can represent it with a native
 * layout object or with binding maps and other managed state; these do not change shader addresses.
 */
interface PipelineLayout : GpuResource