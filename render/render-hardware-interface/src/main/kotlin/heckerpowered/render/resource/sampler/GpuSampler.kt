/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.sampler

import heckerpowered.render.GpuResource

/**
 * A reusable texture-sampling configuration established by a graphics device.
 *
 * A texture view selects the image data available to a shader. A sampler controls how sampling
 * chooses and combines texels from that data, including filtering, mip selection, and
 * coordinate addressing.
 *
 * Keeping these rules separate allows the same texture view to be sampled differently without
 * duplicating its image data. For example, one draw can preserve sharp texel boundaries with
 * nearest filtering while another uses linear filtering for smoother transitions.
 *
 * The configuration remains unchanged after creation and can be reused with multiple compatible
 * texture views. [SamplerDescription] describes the rules used to create it.
 */
interface GpuSampler : GpuResource
