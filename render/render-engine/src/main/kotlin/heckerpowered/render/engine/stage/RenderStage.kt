/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

/**
 * An ordered snapshot of rendering work before geometry is prepared for individual passes.
 *
 * The builder copies the operation list. Referenced pass descriptions and GPU resources are still
 * the caller's selections; creating this value neither records commands nor extends their validity.
 */
internal class RenderStage internal constructor(val operations: List<RenderStageOperation>)
