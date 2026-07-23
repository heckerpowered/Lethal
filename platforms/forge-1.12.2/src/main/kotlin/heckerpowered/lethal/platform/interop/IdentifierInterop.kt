/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.resources.Identifier
import net.minecraft.util.ResourceLocation

object IdentifierInterop {
    fun identifier(identifier: Identifier): ResourceLocation {
        return identifier as? ResourceLocation ?: ResourceLocation(identifier.namespace, identifier.path)
    }

    @JvmStatic
    fun identifier(identifier: ResourceLocation): Identifier {
        return identifier as? Identifier ?: Identifier.create(identifier.namespace, identifier.path)
    }
}

fun Identifier.identifier(): ResourceLocation {
    return IdentifierInterop.identifier(this)
}

fun ResourceLocation.identifier(): Identifier {
    return IdentifierInterop.identifier(this)
}