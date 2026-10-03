/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import kotlin.test.*

class ShaderSourceSnapshotTest {
    @Test
    fun sourceGenerationNormalizesOriginAndRetainsCopiedIncludes() {
        val includes = linkedMapOf("screen/./layout.glsl" to "original layout")
        val snapshot = ShaderSourceSnapshot("screen/nested/../main.frag", "original shader", includes)
        includes["screen/./layout.glsl"] = "changed layout"
        includes.clear()
        assertEquals("screen/main.frag", snapshot.origin)
        assertEquals("original shader", snapshot.text)
        assertEquals("original layout", snapshot.include("screen/layout.glsl"))
        assertFailsWith<IllegalArgumentException> { snapshot.include("screen/./layout.glsl") }
    }

    @Test
    fun quotedAndRootIncludesResolveAgainstTheirOwnSourceOrigins() {
        val snapshot = ShaderSourceSnapshot("screen/nested/main.frag", "shader", mapOf(
            "screen/nested/helpers.glsl" to "relative helper",
            "helpers.glsl" to "root helper",
            "screen/shared/layout.glsl" to "shared layout",
        ))
        val relative = snapshot.resolve("./helpers.glsl", snapshot.origin, relative = true)
        val root = snapshot.resolve("./helpers.glsl", "", relative = false)
        assertEquals("screen/nested/helpers.glsl", relative)
        assertEquals("helpers.glsl", root)
        assertEquals("relative helper", snapshot.include(relative))
        assertEquals("root helper", snapshot.include(root))
        assertEquals("screen/shared/layout.glsl", snapshot.resolve("../shared/./layout.glsl", "screen/nested/include.glsl", relative = true))
        assertEquals("screen/shared/layout.glsl", snapshot.resolve("screen/nested/../shared/layout.glsl", "ignored.frag", relative = false))
    }

    @Test
    fun duplicateIncludeNamesAfterNormalizationRejectInsteadOfOverwriting() {
        for (alias in listOf("./layout.glsl", "folder/../layout.glsl", "folder//../layout.glsl")) {
            val failure = assertFailsWith<IllegalArgumentException> {
                ShaderSourceSnapshot("main.frag", "shader", linkedMapOf("layout.glsl" to "first", alias to "second"))
            }
            assertContains(failure.message.orEmpty(), "Duplicate normalized include: layout.glsl")
        }
    }

    @Test
    fun invalidOriginsIncludePathsAndMissingSourcesReject() {
        val invalid = listOf("", "/main.frag", "../main.frag", "a/../../main.frag", ".", "a/..", "a\\main.frag", "a\u0000main.frag")
        for (name in invalid) {
            assertFailsWith<IllegalArgumentException> { ShaderSourceSnapshot(name, "shader", emptyMap()) }
            assertFailsWith<IllegalArgumentException> { ShaderSourceSnapshot("main.frag", "shader", mapOf(name to "include")) }
        }
        val snapshot = ShaderSourceSnapshot("screen/main.frag", "shader", emptyMap())
        for (name in invalid) assertFailsWith<IllegalArgumentException> { snapshot.resolve(name, snapshot.origin, relative = false) }
        assertFailsWith<IllegalArgumentException> { snapshot.resolve("../../escape.glsl", snapshot.origin, relative = true) }
        val missing = assertFailsWith<IllegalArgumentException> { snapshot.include("missing.glsl") }
        assertContains(missing.message.orEmpty(), "Missing include: missing.glsl")
    }
}
