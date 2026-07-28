/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import java.io.BufferedReader
import java.nio.charset.StandardCharsets

enum class ShaderSourceLanguage {
    GLSL,
}

/**
 * Describes where shader source originates without coupling a shader module to one storage mechanism.
 */
sealed interface ShaderSource {
    val language: ShaderSourceLanguage
    val entryPoint: String
    val label: String

    data class Text(
        val text: String,
        override val language: ShaderSourceLanguage,
        override val entryPoint: String,
        override val label: String,
    ) : ShaderSource {
        init {
            require(text.isNotBlank()) { "Shader source text must not be blank" }
            require(entryPoint.isNotBlank()) { "Shader entry point must not be blank" }
            require(label.isNotBlank()) { "Shader source label must not be blank" }
        }
    }

    data class Asset(
        val namespace: String,
        val path: String,
        override val language: ShaderSourceLanguage,
        override val entryPoint: String,
    ) : ShaderSource {
        init {
            require(namespace.isNotBlank() && '/' !in namespace && '\\' !in namespace && namespace != "." && namespace != "..") { "Shader asset namespace must be one path segment" }
            require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path && ".." !in path.split('/')) { "Shader asset path must be relative and must not traverse parent directories" }
            require(entryPoint.isNotBlank()) { "Shader entry point must not be blank" }
        }

        val resourcePath = "/assets/$namespace/shaders/$path"
        override val label: String
            get() = resourcePath
    }

    companion object {
        fun text(text: String, language: ShaderSourceLanguage = ShaderSourceLanguage.GLSL, entryPoint: String = "main", label: String = "Inline $language shader"): ShaderSource {
            return Text(text, language, entryPoint, label)
        }

        fun asset(namespace: String, path: String, language: ShaderSourceLanguage = ShaderSourceLanguage.GLSL, entryPoint: String = "main"): Asset {
            return Asset(namespace, path, language, entryPoint)
        }
    }
}

/**
 * Loads shader text for an asset-backed [ShaderSource].
 */
fun interface ShaderAssetLoader {
    fun load(source: ShaderSource.Asset): String
}

/**
 * Resolves shader assets from the runtime classpath.
 */
object ClasspathShaderAssetLoader : ShaderAssetLoader {
    override fun load(source: ShaderSource.Asset): String {
        val stream = ShaderSource::class.java.getResourceAsStream(source.resourcePath) ?: error("Shader asset is missing: ${source.resourcePath}")
        return stream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
    }
}

/**
 * Materializes this source only when a backend needs its text representation.
 */
fun ShaderSource.resolve(assetLoader: ShaderAssetLoader): String {
    return when (this) {
        is ShaderSource.Text -> text
        is ShaderSource.Asset -> assetLoader.load(this)
    }
}
