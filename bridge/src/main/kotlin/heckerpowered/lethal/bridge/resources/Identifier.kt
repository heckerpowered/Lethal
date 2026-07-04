package heckerpowered.lethal.bridge.resources

import heckerpowered.lethal.bridge.FreestandingRepresentation
import heckerpowered.lethal.bridge.platform.Services
import heckerpowered.lethal.bridge.platform.loadOrNull

interface Identifier {
    val namespace: String
    val path: String

    fun asString(): String = "$namespace:$path"

    companion object {
        val Provider = IdentifierProvider.Auto

        @JvmStatic
        fun create(namespace: String, path: String): Identifier {
            return Provider.identifier(namespace, path)
        }
    }
}

private data class FreestandingIdentifier(
    override val namespace: String,
    override val path: String,
) : Identifier, FreestandingRepresentation

object FreestandingIdentifierProvider : IdentifierProvider {
    override fun identifier(namespace: String, path: String): Identifier = FreestandingIdentifier(namespace, path)
}

interface IdentifierProvider {
    companion object {
        val Freestanding: IdentifierProvider = FreestandingIdentifierProvider
        val Hosting: IdentifierProvider?
            get() = Services.loadOrNull<IdentifierProvider>()
        val Auto
            get() = Hosting ?: Freestanding
    }

    fun identifier(namespace: String, path: String): Identifier = Freestanding.identifier(namespace, path)
}
