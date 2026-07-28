/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.bridge.resources.asset
import heckerpowered.lethal.Constants
import heckerpowered.render.ClasspathShaderAssetLoader
import heckerpowered.render.ShaderSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShaderAssetTest {
    @Test
    fun identifierResolvesToTheConventionalShaderAsset() {
        val source = ShaderSource.asset(Constants.identifier("world/electric_line.vsh"))

        assertEquals(expected = "/assets/lethal/shaders/world/electric_line.vsh", actual = source.resourcePath)
        assertTrue(actual = ClasspathShaderAssetLoader.load(source).contains("void main"))
    }
}
