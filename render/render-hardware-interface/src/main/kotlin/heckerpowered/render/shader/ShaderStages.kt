/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

import heckerpowered.render.GpuResource

/**
 * A backend-established combination of shader stages intended to be used together by a pipeline.
 *
 * Successful creation establishes that the constituent shader modules form a stage combination
 * accepted by the current graphics backend. Each shader stage occurs at most once.
 *
 * Establishing a stage combination may require backend-specific processing. OpenGL implementations
 * may create and link a program object, while Vulkan implementations may retain the constituent
 * shader modules and defer executable pipeline compilation until pipeline creation.
 *
 * This abstraction therefore represents the logical combination of shader stages rather than a
 * particular native shader-program object or linking model.
 */
interface ShaderStages : GpuResource