/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

/**
 * Groups the shader modules that provide the programmable stages of a render pipeline.
 *
 * A [ShaderModule] contains the code for one shader stage. A [ShaderStages] typically combines
 * modules such as a vertex module and a fragment module so that they can be used together when
 * creating a render pipeline. Each [ShaderStage] may occur at most once.
 *
 * The same established combination may be reused by multiple compatible render pipelines. For
 * example, pipelines may use the same shaders while selecting different rasterization,
 * depth-stencil, or blending state.
 *
 * Successful creation establishes only that the supplied modules can be represented as one stage
 * set. It does not establish compatibility with a particular pipeline layout, vertex input,
 * attachment configuration, or other fixed-function state. Those requirements are established
 * separately during render-pipeline creation.
 *
 * This handle does not own its input modules. Keep modules open until all pipelines that need
 * them have been created: a backend may retain the modules as creation inputs or link a program
 * when establishing the stage combination. Created pipelines do not require their input modules
 * to remain open. A linked program is runtime state and belongs to the lifetime supplied when
 * these stages were created; that lifetime must cover every pipeline use of the program.
 *
 * The stage combination has no separate close obligation.
 *
 * [ShaderStagesDescription] can be constructed and copied freely because it only records which
 * shader modules should be combined. A [ShaderStages] represents the combination after it has been
 * established by a graphics device and may additionally contain implementation-owned state.
 * Copying a description therefore copies only the request; it does not duplicate an established
 * stage combination. The concrete representation of that combination is not exposed by this API.
 */
interface ShaderStages
