/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.resources.Identifier
import net.minecraft.util.ResourceLocation

object IdentifierInterop {
    fun asHost(identifier: Identifier): ResourceLocation {
        return identifier as? ResourceLocation ?: ResourceLocation(identifier.namespace, identifier.path)
    }

    @JvmStatic
    fun asView(identifier: ResourceLocation): Identifier {
        return identifier as? Identifier ?: Identifier.create(identifier.namespace, identifier.path)
    }
}

fun Identifier.asHost(): ResourceLocation {
    return IdentifierInterop.asHost(this)
}

fun ResourceLocation.asView(): Identifier {
    return IdentifierInterop.asView(this)
}
