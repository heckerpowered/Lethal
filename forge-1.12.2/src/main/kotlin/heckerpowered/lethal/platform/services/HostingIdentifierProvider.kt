/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.lethal.bridge.resources.Identifier
import heckerpowered.lethal.bridge.resources.IdentifierProvider
import net.minecraft.util.ResourceLocation

class HostingIdentifierProvider : IdentifierProvider {
    @Suppress("KotlinConstantConditions")
    override fun identifier(namespace: String, path: String): Identifier {
        return ResourceLocation(namespace, path) as Identifier
    }
}