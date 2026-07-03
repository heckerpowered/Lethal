package heckerpowered.lethal.platform.services

import heckerpowered.lethal.bridge.resources.Identifier
import heckerpowered.lethal.bridge.resources.IdentifierProvider
import net.minecraft.util.ResourceLocation

class IdentifierProvider : IdentifierProvider {
    @Suppress("KotlinConstantConditions")
    override fun identifier(namespace: String, path: String): Identifier {
        return ResourceLocation(namespace, path) as Identifier
    }
}