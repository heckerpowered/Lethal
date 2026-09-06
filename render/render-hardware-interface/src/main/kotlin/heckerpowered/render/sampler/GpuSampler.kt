/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.sampler

import heckerpowered.render.GpuResource

/**
 * A reusable sampling configuration established by a graphics device.
 *
 * A texture view selects which texels and mip levels are accessible; a sampler determines how
 * sampling filters those texels and handles texture coordinates. It contains no image storage
 * and does not own the textures used with it.
 *
 * The configuration is immutable after creation. One sampler may be reused with multiple
 * compatible texture views, and a texture view may be used with different samplers.
 *
 * The backend may represent this resource with a native sampler object or with managed sampling
 * state. Successful creation does not establish compatibility with every texture format, view,
 * or simultaneous binding combination; those requirements are checked when the sampler is used.
 */
interface GpuSampler : GpuResource