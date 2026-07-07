/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.adapter.world

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.math.BoxView

/**
 * Host-neutral world access.
 */
interface WorldAccess {
    /**
     * Loaded entities visible to this world access.
     */
    val entities: Sequence<EntityAccess>

    /**
     * Returns entities whose bounding boxes intersect [searchBox].
     *
     * Implementations should preserve lazy traversal when the host exposes spatial entity storage.
     */
    fun getEntities(searchBox: BoxView): Sequence<EntityAccess>
}
