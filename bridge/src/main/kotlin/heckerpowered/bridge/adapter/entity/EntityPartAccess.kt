/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * A physical entity part with a logical parent entity.
 */
interface EntityPartAccess : EntityAccess {
    val parent: EntityAccess
}
