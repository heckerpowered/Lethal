/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * Redirects experience that the host creates after its ordinary death callback has returned.
 *
 * Hosts that create experience during the death callback do not need to expose this capability.
 */
interface DeferredExperienceDropAccess {
    fun sendDeferredExperienceTo(receiver: ExperienceReceiverAccess)
}
