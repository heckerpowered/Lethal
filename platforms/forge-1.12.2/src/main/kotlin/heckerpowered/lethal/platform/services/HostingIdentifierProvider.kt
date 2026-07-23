/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.resources.IdentifierProvider
import net.minecraft.util.ResourceLocation

class HostingIdentifierProvider : IdentifierProvider {
    override fun identifier(namespace: String, path: String): Identifier {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return ResourceLocation(namespace, path) as? Identifier ?: IdentifierProvider.Freestanding.identifier(namespace, path)
    }
}