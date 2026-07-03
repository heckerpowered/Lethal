package heckerpowered.lethal.platform

import heckerpowered.lethal.bridge.resources.Identifier
import net.minecraft.util.ResourceLocation

object IdentifierInterop {
    fun identifier(identifier: Identifier): ResourceLocation {
        return identifier as? ResourceLocation ?: ResourceLocation(identifier.namespace, identifier.path)
    }

    fun identifier(identifier: ResourceLocation): Identifier {
        return identifier as? Identifier ?: Identifier.create(identifier.namespace, identifier.path)
    }
}