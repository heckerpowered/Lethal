/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.resources

import heckerpowered.bridge.FreestandingRepresentation
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.platform.loadOrNull
import heckerpowered.bridge.security.Untrusted

interface Identifier {
    val namespace: String
    val path: String

    fun asString(): String = "$namespace:$path"

    companion object {
        const val NAMESPACE_SEPARATOR = ':'
        const val DEFAULT_NAMESPACE = "minecraft"
        const val MAXIMUM_LENGTH = 256

        val Provider = IdentifierProvider.Auto

        fun create(namespace: String, path: String): Identifier {
            check(isValidNamespace(namespace))
            check(isValidPath(path))
            return Provider.identifier(namespace, path)
        }

        fun parse(value: String): Untrusted<Identifier> {
            return parseOrNull(value) ?: error("Invalid identifier")
        }

        fun tryParse(value: String): Untrusted<Identifier>? {
            return parseOrNull(value)
        }

        private fun parseOrNull(value: String): Untrusted<Identifier>? {
            if (value.length > MAXIMUM_LENGTH) return null

            val separatorIndex = value.indexOf(NAMESPACE_SEPARATOR)
            val namespace = if (separatorIndex > 0) value.substring(0, separatorIndex) else DEFAULT_NAMESPACE
            val path = if (separatorIndex >= 0) value.substring(separatorIndex + 1) else value

            if (!isValidNamespace(namespace) || !isValidPath(path)) return null
            return Untrusted(Provider.identifier(namespace, path))
        }

        private fun isValidNamespace(namespace: String): Boolean {
            return namespace.indices.all { index ->
                val character = namespace[index]
                isValidNamespaceCharacter(character) && !isRepeatedDot(namespace, index)
            }
        }

        private fun isValidPath(path: String): Boolean {
            return path.indices.all { index ->
                val character = path[index]
                isValidPathCharacter(character) && !isRepeatedDot(path, index)
            }
        }

        private fun isRepeatedDot(value: String, index: Int): Boolean {
            return value[index] == '.' && index + 1 < value.length && value[index + 1] == '.'
        }

        private fun isValidNamespaceCharacter(character: Char): Boolean {
            return character == '_' || character == '-' || character == '.' || character in 'a'..'z' || character in '0'..'9'
        }

        private fun isValidPathCharacter(character: Char): Boolean {
            return character == '/' || isValidNamespaceCharacter(character)
        }
    }
}

private data class FreestandingIdentifier(
    override val namespace: String,
    override val path: String,
) : Identifier, FreestandingRepresentation {
    override fun toString() = "$namespace:$path"
}

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
