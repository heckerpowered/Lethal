/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.rule

interface ServerUpdateRule {
    /**
     * Advances runtime state once after the host completes an authoritative server update.
     */
    fun onServerUpdate()
}
