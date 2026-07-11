/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.resources.IdentifierProvider
import net.minecraft.util.ResourceLocation

class HostingIdentifierProvider : IdentifierProvider {
    @Suppress("KotlinConstantConditions")
    override fun identifier(namespace: String, path: String): Identifier {
        return ResourceLocation(namespace, path) as Identifier
    }
}