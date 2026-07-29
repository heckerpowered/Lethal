/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.resources.IdentifierProvider
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.resources.ResourceLocation

class HostingIdentifierProvider : IdentifierProvider {
    override fun identifier(namespace: String, path: String): Identifier {
        return ResourceLocation.fromNamespaceAndPath(namespace, path).asView()
    }
}
