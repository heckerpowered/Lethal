/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import heckerpowered.render.opengl.shader.OpenGLShaderArtifact
import heckerpowered.render.opengl.shader.OpenGLShaderBindingKind
import heckerpowered.render.shader.ShaderStage
import java.nio.ByteBuffer
import kotlin.test.*

class OpenGLUniformBlockTest {
    @Test
    fun actualProducerRetainsAnonymous140BlocksAndAdds120OrdinaryUniforms() {
        GlslCompiler().use { compiler ->
            val stages = listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
                val output = if (stage == ShaderStage.Vertex) "gl_Position" else "color"
                val result = if (stage == ShaderStage.Vertex) "" else "layout(location=0) out vec4 color;"
                val source = """
                    #version 450
                    layout(set=0,binding=1,std140) uniform First { vec4 value; mat4 transform; } first;
                    layout(set=2,binding=3,std140) uniform Second { vec4 value; layout(row_major) mat4 transform; } second;
                    $result
                    void main(){ $output = first.transform * first.value + second.transform * second.value; }
                """.trimIndent()
                val shader = compiler.compile(source, "multiple-${stage.name}.glsl", stage)
                val original = bytes(shader.spirV())
                val reflection = shaderInterfaceArtifact(shader).encode()
                val direct = lowerOpenGLTarget(shader, 140)
                val artifact = lowerToOpenGL(shader)
                assertEquals(140, artifact.glslVersion)
                assertEquals(direct, artifact.copy(plainUniformSource = null))
                val plain = assertNotNull(artifact.plainUniformSource)
                assertContains(plain, "#version 120")
                assertEquals(plain, lowerOpenGLTarget(shader, 120).source)
                assertEquals(direct, lowerOpenGLTarget(shader, 140))
                assertFalse(artifact.source.contains("rhi_uniform_"))
                assertFalse(artifact.source.contains("#version 150"))
                for (binding in artifact.bindings) {
                    assertEquals(OpenGLShaderBindingKind.UniformBuffer, binding.kind)
                    assertEquals(1, binding.count)
                    assertContains(artifact.source, "uniform ${binding.name}")
                    assertContains(artifact.source, "vec4 ${binding.name}_member_0;")
                    assertContains(artifact.source, "mat4 ${binding.name}_member_1;")
                    assertEquals(80, binding.sizeBytes)
                    assertEquals(if (binding.set == 0) "0:4:1:0:false;16:4:4:16:false" else "0:4:1:0:false;16:4:4:16:true", binding.blockSignature)
                }
                assertEquals(listOf(0 to 1, 2 to 3), artifact.bindings.map { it.set to it.binding }.sortedBy { it.first })
                assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(artifact.encode())))
                assertContentEquals(original, bytes(shader.spirV()))
                assertContentEquals(reflection, shaderInterfaceArtifact(shader).encode())
                artifact
            }
            assertEquals(stages[0].bindings, stages[1].bindings)
        }
    }

    @Test
    fun singlePublicUboCoexistsWithSamplerAndPushPruningWithoutChangingCanonicalFacts() {
        val source = """
            #version 450
            layout(set=0,binding=7,std140) uniform Tint { vec4 color; } tint;
            layout(set=0,binding=9) uniform sampler2D image;
            layout(push_constant) uniform Fill { vec4 unused; vec4 multiplier; } fill;
            layout(location=0) out vec4 color;
            void main(){color=tint.color*texture(image,vec2(0.5))*fill.multiplier;}
        """.trimIndent()
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(source, "public-ubo.frag", ShaderStage.Fragment)
            val original = bytes(shader.spirV())
            val facts = shaderInterfaceArtifact(shader).encode()
            val artifact = lowerToOpenGL(shader)
            assertContains(artifact.source, "rhi_block_0_7_member_0")
            assertContains(artifact.source, "rhi_texture_0_9")
            assertContains(artifact.source, "1.0 - 0.5")
            assertContains(artifact.source, "rhi_push_fragment.member_1")
            assertFalse(artifact.source.contains("vec4 member_0;"))
            assertEquals(16, artifact.pushConstants.single().offsetBytes)
            assertContentEquals(original, bytes(shader.spirV()))
            assertContentEquals(facts, shaderInterfaceArtifact(shader).encode())
        }
    }

    @Test
    fun generatedNormalizationRejectsUnexpectedDeclarationsAndWholeBlockUses() {
        GlslCompiler().use { compiler ->
            val shader = compiler.compile("#version 450\nlayout(set=2,binding=3,std140) uniform Shift {vec4 offset;} shift;\nvoid main(){gl_Position=shift.offset;}", "guard.vert", ShaderStage.Vertex)
            val binding = lowerToOpenGL(shader).bindings.single()
            val valid = "layout(std140) uniform rhi_block_2_3 { vec4 rhi_block_2_3_member_0; } rhi_uniform_2_3;\nvoid main(){gl_Position=rhi_uniform_2_3.rhi_block_2_3_member_0;}"
            val normalized = anonymizeOpenGLUniformBlocks(valid, listOf(binding))
            assertFalse(normalized.contains("rhi_uniform_2_3"))
            for (invalid in listOf(
                valid.replace("} rhi_uniform_2_3;", "} other;"),
                valid.replace("vec4 rhi_block_2_3_member_0;", "float rhi_block_2_3_member_0;"),
                valid.replace("vec4 rhi_block_2_3_member_0;", "vec4 rhi_block_2_3_member_0; int extra;"),
                valid.replace("rhi_uniform_2_3.rhi_block_2_3_member_0", "rhi_uniform_2_3"),
                valid.replace("rhi_uniform_2_3.rhi_block_2_3_member_0", "rhi_uniform_2_3.other"),
                valid.replace("rhi_uniform_2_3.rhi_block_2_3_member_0", "(rhi_uniform_2_3).rhi_block_2_3_member_0"),
                valid.replace("rhi_uniform_2_3.rhi_block_2_3_member_0", "rhi_uniform_2_3[0].rhi_block_2_3_member_0"),
                valid.replace("rhi_uniform_2_3.rhi_block_2_3_member_0", "other.rhi_uniform_2_3.rhi_block_2_3_member_0"),
                "${valid}\nlayout(std140) uniform rhi_block_2_3 {vec4 rhi_block_2_3_member_0;} rhi_uniform_2_3;",
            )) assertFailsWith<IllegalArgumentException> { anonymizeOpenGLUniformBlocks(invalid, listOf(binding)) }
            assertEquals(valid, anonymizeOpenGLUniformBlocks(valid, emptyList()))
        }
    }

    @Test
    fun generatedNormalizationKeepsCommentsAndEscapedStringContentsOutsideCode() {
        GlslCompiler().use { compiler ->
            val shader = compiler.compile("#version 450\nlayout(set=2,binding=3,std140) uniform Shift {vec4 offset;} shift;\nvoid main(){gl_Position=shift.offset;}", "tokens.vert", ShaderStage.Vertex)
            val binding = lowerToOpenGL(shader).bindings.single()
            val valid = "layout(std140) uniform rhi_block_2_3 { vec4 rhi_block_2_3_member_0; } rhi_uniform_2_3;\nvoid main(){gl_Position=rhi_uniform_2_3.rhi_block_2_3_member_0;}\n"
            val suffixes = listOf(
                "// uniform rhi_block_2_3 { rhi_uniform_2_3.other };\n",
                "/* uniform rhi_block_2_3 { rhi_uniform_2_3.other }; */\n",
                """debugPrintfEXT("rhi_uniform_2_3.other \" uniform rhi_block_2_3 { \\ path");""",
            )
            for (suffix in suffixes) {
                val normalized = anonymizeOpenGLUniformBlocks("$valid$suffix", listOf(binding))
                assertEquals(suffix, normalized.takeLast(suffix.length))
                assertContains(normalized, "gl_Position=rhi_block_2_3_member_0;")
                assertFalse(normalized.removeSuffix(suffix).contains("rhi_uniform_2_3"))
            }
            val comments = valid.replace("rhi_uniform_2_3.rhi_block_2_3_member_0", "rhi_uniform_2_3 /* member selection */ . rhi_block_2_3_member_0")
            assertContains(anonymizeOpenGLUniformBlocks(comments, listOf(binding)), "gl_Position= rhi_block_2_3_member_0;")
        }
    }

    @Test
    fun actualProducerPreservesBindingsWhenPrivateSymbolsMatchPromotedMembers() {
        val header = "#version 450\nlayout(set=0,binding=0,std140) uniform Tint {vec4 color;} tint;\nlayout(location=0) out vec4 color;\n"
        val bodies = listOf(
            "void main(){vec4 rhi_block_0_0_member_0=vec4(.25);color=tint.color+rhi_block_0_0_member_0;}",
            "vec4 combine(vec4 rhi_block_0_0_member_0){return tint.color+rhi_block_0_0_member_0;} void main(){color=combine(vec4(.25));}",
            "vec4 rhi_block_0_0_member_0=vec4(.25);void main(){color=tint.color+rhi_block_0_0_member_0;}",
            "vec4 rhi_block_0_0_member_0(){return vec4(.25);}void main(){color=tint.color+rhi_block_0_0_member_0();}",
            "struct Other{vec4 rhi_block_0_0_member_0;};void main(){Other other=Other(vec4(.25));color=tint.color+other.rhi_block_0_0_member_0;}",
            "void main(){vec4 rhi_block_0_0_member_0=vec4(.125);color=tint.color+rhi_block_0_0_member_0;{vec4 rhi_block_0_0_member_0=vec4(.125);color+=rhi_block_0_0_member_0;}}",
            "struct rhi_block_0_0_member_0{vec4 value;};void main(){rhi_block_0_0_member_0 other=rhi_block_0_0_member_0(vec4(.25));color=tint.color+other.value;}",
        )
        GlslCompiler().use { compiler ->
            for ((index, body) in bodies.withIndex()) {
                val shader = compiler.compile("$header$body", "hygiene-$index.frag", ShaderStage.Fragment)
                val original = bytes(shader.spirV())
                val facts = shaderInterfaceArtifact(shader).encode()
                val artifact = lowerToOpenGL(shader)
                assertContains(artifact.source, "rhi_ubo_internal_")
                // Only the actual uniform declaration and its read keep the promoted name.
                assertEquals(2, Regex("\\brhi_block_0_0_member_0\\b").findAll(artifact.source).count())
                assertEquals(artifact.copy(plainUniformSource = null), lowerOpenGLTarget(shader, 140))
                assertEquals(artifact, lowerToOpenGL(shader))
                assertContentEquals(original, bytes(shader.spirV()))
                assertContentEquals(facts, shaderInterfaceArtifact(shader).encode())
            }
        }
    }

    @Test
    fun conflictingPrivateNamesCannotChangeSharedBlockNamesAcrossStages() {
        GlslCompiler().use { compiler ->
            val header = "#version 450\nlayout(set=2,binding=3,std140)uniform Shared{vec4 value;}block;\n"
            val vertex = compiler.compile("${header}void main(){vec4 rhi_block_2_3_member_0=vec4(.25);gl_Position=block.value+rhi_block_2_3_member_0;}", "shared.vert", ShaderStage.Vertex)
            val fragment = compiler.compile("${header}layout(location=0)out vec4 color;vec4 combine(vec4 rhi_block_2_3_member_0){return block.value+rhi_block_2_3_member_0;}void main(){color=combine(vec4(.25));}", "shared.frag", ShaderStage.Fragment)
            val stages = listOf(vertex, fragment).map { lowerToOpenGL(it) }
            assertEquals(stages[0].bindings, stages[1].bindings)
            for (artifact in stages) assertContains(artifact.source, "vec4 rhi_block_2_3_member_0;")
        }
    }

    @Test
    fun replacementNamesAvoidExistingSymbolsRatherThanRelyingOnAPrefix() {
        val header = "#version 450\nlayout(set=0,binding=0,std140) uniform Tint{vec4 color;}tint;layout(location=0)out vec4 color;"
        val cases = listOf(
            "vec4 reserved=vec4(.125);void main(){vec4 rhi_block_0_0_member_0=vec4(.125);color=tint.color+rhi_block_0_0_member_0+reserved;}" to ";",
            "vec4 reserved(){return vec4(.125);}void main(){vec4 rhi_block_0_0_member_0=vec4(.125);color=tint.color+rhi_block_0_0_member_0+reserved();}" to "()",
        )
        GlslCompiler().use { compiler ->
            for ((body, declarationSuffix) in cases) {
                val first = lowerToOpenGL(compiler.compile("$header$body", "replacement.frag", ShaderStage.Fragment))
                val proposed = Regex("vec4 (rhi_ubo_internal_[0-9]+) =").find(first.source)!!.groupValues[1]
                val shader = compiler.compile("$header${body.replace("reserved", proposed)}", "reserved-replacement.frag", ShaderStage.Fragment)
                val artifact = lowerToOpenGL(shader)
                assertContains(artifact.source, "vec4 $proposed$declarationSuffix")
                assertContains(artifact.source, "vec4 ${proposed}_0 =")
                assertEquals(2, Regex("\\brhi_block_0_0_member_0\\b").findAll(artifact.source).count())
                assertEquals(first.bindings, artifact.bindings)
            }
        }
    }

    @Test
    fun generatedNormalizationRejectsAnyUnclaimedPromotedNameOccurrence() {
        GlslCompiler().use { compiler ->
            val shader = compiler.compile("#version 450\nlayout(set=0,binding=0,std140)uniform Tint{vec4 color;}tint;layout(location=0)out vec4 color;void main(){color=tint.color;}", "residual.frag", ShaderStage.Fragment)
            val binding = lowerToOpenGL(shader).bindings.single()
            val valid = "layout(std140)uniform rhi_block_0_0{vec4 rhi_block_0_0_member_0;}rhi_uniform_0_0;void main(){color=rhi_uniform_0_0.rhi_block_0_0_member_0;}"
            for (extra in listOf(
                "void extra(){vec4 rhi_block_0_0_member_0=vec4(.25);}",
                "vec4 extra(vec4 rhi_block_0_0_member_0){return rhi_block_0_0_member_0;}",
                "vec4 rhi_block_0_0_member_0=vec4(.25);",
                "vec4 rhi_block_0_0_member_0(){return vec4(.25);}",
                "struct Other{vec4 rhi_block_0_0_member_0;};",
                "void extra(){color+=rhi_block_0_0_member_0;}",
            )) assertFailsWith<IllegalArgumentException> { anonymizeOpenGLUniformBlocks("$valid$extra", listOf(binding)) }
            val comment = "/* rhi_block_0_0_member_0 */ // rhi_block_0_0_member_0\n"
            assertTrue(anonymizeOpenGLUniformBlocks("$valid$comment", listOf(binding)).endsWith(comment))
        }
    }

    private fun bytes(buffer: ByteBuffer) = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }
}
