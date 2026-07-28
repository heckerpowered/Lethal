/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import kotlin.test.Test
import kotlin.test.assertEquals

class ShaderSourceTest {
    @Test
    fun assetSourceDerivesTheMinecraftResourcePath() {
        val source = ShaderSource.asset("lethal", "world/electric_line.vsh")

        assertEquals(expected = "/assets/lethal/shaders/world/electric_line.vsh", actual = source.resourcePath)
        assertEquals(expected = source.resourcePath, actual = source.label)
    }

    @Test
    fun textSourceDoesNotInvokeTheAssetLoader() {
        val source = ShaderSource.text("void main() {}")
        var loadCount = 0

        val resolved = source.resolve {
            loadCount++
            error("Text shader source must not load an asset")
        }

        assertEquals(expected = "void main() {}", actual = resolved)
        assertEquals(expected = 0, actual = loadCount)
    }

    @Test
    fun shaderProgramVariantDerivesModuleStagesFromItsSources() {
        val vertexSource = ShaderSource.asset("lethal", "world/electric_line.vsh")
        val fragmentSource = ShaderSource.asset("lethal", "world/electric_line.fsh")

        val variant = ShaderProgramVariant(vertexSource, fragmentSource, GraphicsFeature.NativeUniformBuffers)

        assertEquals(expected = ShaderStage.Vertex, actual = variant.vertexShader.stage)
        assertEquals(expected = ShaderStage.Fragment, actual = variant.fragmentShader.stage)
        assertEquals(expected = setOf(GraphicsFeature.NativeUniformBuffers), actual = variant.requiredFeatures)
    }
}
