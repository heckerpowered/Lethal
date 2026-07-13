/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render

interface ClientPostProcessRule {
    fun onPostProcess(context: ClientPostProcessContext)
}
