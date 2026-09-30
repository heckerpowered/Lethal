/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.material

/**
 * Pass-independent appearance information used when lowering rendering contributions.
 *
 * A material describes what an object intends to look like, not the final RHI state of any one
 * pass. Main, depth, shadow, and other mesh-pass processors may therefore interpret the same
 * material differently when selecting shaders, pipelines, and resource bindings.
 *
 * This interface intentionally has no members yet. The first concrete materials should establish
 * which properties are genuinely pass-independent instead of making the base abstraction mirror
 * a particular shader or [heckerpowered.render.RenderPipelineDescription].
 */
interface Material
