/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView

/**
 * Executes an entity independently of ordinary damage immunity.
 *
 * Implementations must attribute death to [source], invoke the host's normal death processing,
 * and keep the entity's observable health at zero while it remains in the world.
 */
interface EntityExecutionAccess {
    fun execute(source: DamageSourceView)
}
