/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * An entity represented by physical entity parts for collision queries.
 */
interface MultipartEntityAccess : EntityAccess {
    /**
     * The host-owned physical entities used for collision queries.
     *
     * Consumers must treat the returned array as read-only.
     */
    val parts: Array<out EntityPartAccess>
}
