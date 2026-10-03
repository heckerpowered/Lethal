/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import heckerpowered.render.opengl.shader.OpenGLShaderArtifact
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.reflection.ShaderInterfaceArtifact
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.*

class ShaderCompilerMainTest {
    @Test
    fun supportedSuffixesPublishAllArtifactsAndIgnoreUnrelatedFiles() {
        val directory = Files.createTempDirectory("rhi-compiler-cli")
        try {
            val source = Files.createDirectory(directory.resolve("source"))
            val output = directory.resolve("output")
            val stages = mapOf("shader.vert" to ShaderStage.Vertex, "shader.VSH" to ShaderStage.Vertex, "shader.frag" to ShaderStage.Fragment, "shader.FSH" to ShaderStage.Fragment)
            for ((name, stage) in stages) {
                val text = when (stage) {
                    ShaderStage.Vertex -> "#version 450\nvoid main(){gl_Position=vec4(0,0,0,1);}"
                    ShaderStage.Fragment -> "#version 450\nlayout(location=0) out vec4 result;\nvoid main(){result=vec4(1);}"
                }
                source.resolve(name).writeText(text)
            }
            source.resolve("readme.txt").writeText("not a shader")
            main(arrayOf(source.toString(), output.toString()))
            Files.list(output).use { assertEquals(12L, it.count()) }
            for ((name, stage) in stages) {
                val spirV = Files.readAllBytes(output.resolve("$name.spv"))
                assertEquals(0x07230203, ByteBuffer.wrap(spirV).order(ByteOrder.LITTLE_ENDIAN).int)
                val metadata = ShaderInterfaceArtifact.decode(ByteBuffer.wrap(Files.readAllBytes(output.resolve("$name.rhif"))))
                assertEquals(stage, metadata.stage)
                assertTrue(metadata.matchesSpirV(ByteBuffer.wrap(spirV)))
                val artifact = OpenGLShaderArtifact.decode(ByteBuffer.wrap(Files.readAllBytes(output.resolve("$name.rhigl"))))
                assertEquals(stage, artifact.stage)
                assertTrue(artifact.source.isNotEmpty())
                assertTrue(artifact.coreSource.isNotEmpty())
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
