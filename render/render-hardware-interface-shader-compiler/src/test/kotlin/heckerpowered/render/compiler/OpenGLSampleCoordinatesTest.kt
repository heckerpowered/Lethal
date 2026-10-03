/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import heckerpowered.render.opengl.shader.OpenGLShaderArtifact
import heckerpowered.render.shader.ShaderStage
import org.lwjgl.util.spvc.Spv.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Paths
import kotlin.test.*

class OpenGLSampleCoordinatesTest {
    @Test
    fun realCopyTentAndBrightnessLowerEachFinalSampleAndPreserveCanonicalArtifacts() {
        GlslCompiler().use { compiler ->
            val root = Paths.get("src/test/resources/interface-fixtures")
            for (name in listOf("copy.frag", "tent.frag", "brightness.frag")) {
                val shader = compiler.compile(root, root.resolve(name), ShaderStage.Fragment)
                val original = words(shader.spirV())
                val reflection = shaderInterfaceArtifact(shader).encode()
                val lowered = lowerOpenGLSampleCoordinates(shader.spirV(), shader.stage)
                assertSampleAdaptation(original, words(lowered))
                val core = lowerOpenGLTarget(shader, 140)
                val legacy = lowerOpenGLTarget(shader, 120)
                val artifact = lowerToOpenGL(shader)
                assertEquals(legacy.source, artifact.source)
                assertEquals(core.source, artifact.coreSource)
                for (source in listOf(artifact.source, artifact.coreSource)) {
                    assertContains(source, "1.0 -")
                    assertContains(source, "rhi_texture_0_0")
                }
                assertEquals(core, lowerOpenGLTarget(shader, 140))
                assertEquals(legacy, lowerOpenGLTarget(shader, 120))
                assertContentEquals(original, words(shader.spirV()))
                assertContentEquals(reflection, shaderInterfaceArtifact(shader).encode())
                assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(artifact.encode())))
            }
        }
    }

    @Test
    fun computedCoordinatesAndSharedValuesAreAdaptedOnlyAtEachSampleUse() {
        val body = """
            vec2 computed(vec2 value) { return value * vec2(.7, .3) + vec2(.1, .2); }
            void main() {
                vec2 reused = computed(coordinates);
                vec2 selected = coordinates.x > .5 ? reused : coordinates.yx;
                result = texture(image, selected + vec2(.03, -.07));
                result += texture(other, reused) + texture(image, reused);
                result += vec4(reused, dFdx(selected));
            }
        """.trimIndent()
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(fragment(body, "layout(set=2,binding=7) uniform sampler2D other;"), "computed.frag", ShaderStage.Fragment)
            val original = words(shader.spirV())
            assertEquals(3, instructions(original).count { opcode(it) == SpvOpImageSampleImplicitLod })
            assertSampleAdaptation(original, words(lowerOpenGLSampleCoordinates(shader.spirV(), shader.stage)))
            val artifact = lowerToOpenGL(shader)
            assertEquals(listOf(0 to 0, 2 to 7), artifact.bindings.map { it.set to it.binding }.sortedBy { it.first })
            for (source in listOf(artifact.source, artifact.coreSource)) {
                assertEquals(3, "1.0 -".toRegex().findAll(source).count())
                assertContains(source, "computed(")
                assertContains(source, "dFdx(")
            }
            assertContentEquals(original, words(shader.spirV()))
        }
    }

    @Test
    fun samplesInMultipleFunctionsShareOneConstantBeforeAllFunctionBodies() {
        val body = """
            vec4 sampleAt(vec2 value) { return texture(image, value); }
            void main() { result = sampleAt(coordinates) + texture(image, coordinates.yx); }
        """.trimIndent()
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(fragment(body), "functions.frag", ShaderStage.Fragment)
            val original = words(shader.spirV())
            assertEquals(2, instructions(original).count { opcode(it) == SpvOpFunction })
            assertEquals(2, instructions(original).count { opcode(it) == SpvOpImageSampleImplicitLod })
            assertSampleAdaptation(original, words(lowerOpenGLSampleCoordinates(shader.spirV(), shader.stage)))
            val artifact = lowerToOpenGL(shader)
            assertEquals(2, "1.0 -".toRegex().findAll(artifact.source).count())
            assertEquals(2, "1.0 -".toRegex().findAll(artifact.coreSource).count())
            assertContentEquals(original, words(shader.spirV()))
        }
    }

    @Test
    fun effectiveImageOperandsAndUnsupportedSamplesRejectBeforeSpvc() {
        val expressions = listOf(
            "texture(image, coordinates, .2)",
            "textureLod(image, coordinates, 0.0)",
            "textureGrad(image, coordinates, vec2(1,0), vec2(0,1))",
            "textureOffset(image, coordinates, ivec2(0,1))",
            "textureProj(image, vec3(coordinates, 1))",
            "texelFetch(image, ivec2(0,1), 0)",
            "textureGather(image, coordinates)",
        )
        GlslCompiler().use { compiler ->
            for (expression in expressions) {
                val shader = compiler.compile(fragment("void main(){result=$expression;}"), "unsupported.frag", ShaderStage.Fragment)
                val original = words(shader.spirV())
                for (version in listOf(120, 140)) {
                    val failure = assertFailsWith<IllegalArgumentException> { lowerOpenGLTarget(shader, version) }
                    assertContains(failure.message!!, "coordinate lowering")
                }
                assertContentEquals(original, words(shader.spirV()))
            }
            val shadow = compiler.compile(fragment("void main(){result=vec4(texture(image, vec3(coordinates,.5)));}")
                .replace("sampler2D image", "sampler2DShadow image"), "shadow.frag", ShaderStage.Fragment)
            assertFailsWith<IllegalArgumentException> { lowerToOpenGL(shadow) }
        }
    }

    @Test
    fun unsupportedImageShapesAndVertexSamplingReject() {
        GlslCompiler().use { compiler ->
            for ((type, coordinate) in listOf("sampler2DArray" to "vec3(coordinates,0)", "sampler3D" to "vec3(coordinates,0)", "samplerCube" to "vec3(coordinates,1)", "isampler2D" to "coordinates")) {
                val shader = compiler.compile(fragment("void main(){result=vec4(texture(image,$coordinate));}")
                    .replace("sampler2D image", "$type image"), "shape.frag", ShaderStage.Fragment)
                assertFailsWith<IllegalArgumentException> { lowerToOpenGL(shader) }
            }
            val vertex = compiler.compile("#version 450\nlayout(set=0,binding=0) uniform sampler2D image;\nvoid main(){gl_Position=texture(image,vec2(.2,.8));}", "sample.vert", ShaderStage.Vertex)
            assertFailsWith<IllegalArgumentException> { lowerToOpenGL(vertex) }
        }
    }

    @Test
    fun coordinateFreeQueriesAndUnrelatedArithmeticRemainUnchanged() {
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(fragment("void main(){result=vec4(vec2(textureSize(image,0)) + dFdx(coordinates),0,1);}"), "size.frag", ShaderStage.Fragment)
            val original = words(shader.spirV())
            assertTrue(instructions(original).any { opcode(it) == SpvOpImageQuerySizeLod })
            assertContentEquals(original, words(lowerOpenGLSampleCoordinates(shader.spirV(), shader.stage)))
            assertContains(lowerOpenGLTarget(shader, 140).source, "textureSize(")
            val lod = compiler.compile(fragment("void main(){result=vec4(textureQueryLod(image,coordinates),0,1);}"), "query-lod.frag", ShaderStage.Fragment)
            assertFailsWith<IllegalArgumentException> { lowerToOpenGL(lod) }
            val vertex = compiler.compile("#version 450\nvoid main(){gl_Position=vec4(.2,.8,0,1);}", "plain.vert", ShaderStage.Vertex)
            assertContentEquals(words(vertex.spirV()), words(lowerOpenGLSampleCoordinates(vertex.spirV(), vertex.stage)))
        }
    }

    @Test
    fun omittedAndExplicitNoneImageOperandsHaveIdenticalAdaptation() {
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(fragment("void main(){result=texture(image,coordinates);}"), "none.frag", ShaderStage.Fragment)
            val original = words(shader.spirV())
            val explicitNone = replaceSample(original) { sample ->
                (sample + 0).also { it[0] = (6 shl 16) or SpvOpImageSampleImplicitLod }
            }
            assertSampleAdaptation(explicitNone, words(lowerOpenGLSampleCoordinates(buffer(explicitNone), shader.stage)))
        }
    }

    @Test
    fun sparseAndExtensionImageOpcodesRejectIndividuallyWithoutBroadOpcodeRanges() {
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(fragment("void main(){result=texture(image,coordinates);}"), "opcodes.frag", ShaderStage.Fragment)
            val original = words(shader.spirV())
            val unsupported = listOf(
                SpvOpImageSampleExplicitLod, SpvOpImageSampleDrefImplicitLod, SpvOpImageSampleDrefExplicitLod,
                SpvOpImageSampleProjImplicitLod, SpvOpImageSampleProjExplicitLod,
                SpvOpImageSampleProjDrefImplicitLod, SpvOpImageSampleProjDrefExplicitLod,
                SpvOpImageFetch, SpvOpImageGather, SpvOpImageDrefGather, SpvOpImageRead, SpvOpImageWrite,
                SpvOpImageTexelPointer, SpvOpImageQueryLod,
                SpvOpImageSparseSampleImplicitLod, SpvOpImageSparseSampleExplicitLod,
                SpvOpImageSparseSampleDrefImplicitLod, SpvOpImageSparseSampleDrefExplicitLod,
                SpvOpImageSparseSampleProjImplicitLod, SpvOpImageSparseSampleProjExplicitLod,
                SpvOpImageSparseSampleProjDrefImplicitLod, SpvOpImageSparseSampleProjDrefExplicitLod,
                SpvOpImageSparseFetch, SpvOpImageSparseGather, SpvOpImageSparseDrefGather, SpvOpImageSparseRead,
                SpvOpImageSampleWeightedQCOM, SpvOpImageBoxFilterQCOM, SpvOpImageSampleFootprintNV,
            )
            for (operation in unsupported) {
                val changed = replaceSample(original) { it.copyOf().also { sample -> sample[0] = (sample.size shl 16) or operation } }
                val failure = assertFailsWith<IllegalArgumentException> { lowerOpenGLSampleCoordinates(buffer(changed), shader.stage) }
                assertContains(failure.message!!, "image operation $operation")
            }
        }
    }

    @Test
    fun malformedTrustedInputFailsWithBoundsAndSchemaDiagnostics() {
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(fragment("void main(){result=texture(image,coordinates);}"), "malformed.frag", ShaderStage.Fragment)
            val original = words(shader.spirV())
            val invalid = mutableListOf<IntArray>()
            invalid += original.copyOf().also { it[0] = 0 }
            invalid += original.copyOf().also { it[1] = 0x00010300 }
            invalid += original.copyOf().also { it[3] = 0 }
            invalid += original.copyOf().also { it[4] = 1 }
            invalid += original.copyOf().also { it[5] = it[5] and 0xffff }
            invalid += original.copyOf().also { it[5] = (0xffff shl 16) or (it[5] and 0xffff) }
            invalid += original.copyOf(original.size - 1)
            invalid += replaceSample(original) { it.copyOf().also { sample -> sample[3] = original[3] } }
            invalid += replaceSample(original) { it.copyOf().also { sample -> sample[4] = 0 } }
            invalid += replaceSample(original) { it.copyOf().also { sample -> sample[4] = sample[3] } }
            invalid += replaceSample(original) { it.copyOf().also { sample -> sample[2] = sample[4] } }
            invalid += replaceSample(original) { it.copyOf().also { sample -> sample[1] = sample[3] } }
            invalid += original.copyOf().also { updated ->
                val offset = offsets(updated).single { opcodeAt(updated, it) == SpvOpEntryPoint }
                updated[offset + 1] = SpvExecutionModelVertex
            }
            invalid += original.copyOf().also { it[3] = Int.MAX_VALUE }
            invalid += original.copyOf().also { it[3] = 0x3fffff }
            for ((operation, operand, value) in listOf(
                Triple(SpvOpTypeFloat, 2, 64), Triple(SpvOpTypeImage, 5, 1),
                Triple(SpvOpTypeImage, 6, 1), Triple(SpvOpTypeImage, 4, 1),
            )) {
                invalid += original.copyOf().also { updated ->
                    val offset = offsets(updated).single { opcodeAt(updated, it) == operation }
                    updated[offset + operand] = value
                }
            }
            for (input in invalid) {
                assertFailsWith<IllegalArgumentException> { lowerOpenGLSampleCoordinates(buffer(input), shader.stage) }
            }
            assertFailsWith<IllegalArgumentException> { lowerOpenGLSampleCoordinates(ByteBuffer.allocate(7), shader.stage) }
            assertFailsWith<IllegalArgumentException> { lowerOpenGLSampleCoordinates(ByteBuffer.allocate(original.size * 4 + 1), shader.stage) }
            assertContentEquals(original, words(shader.spirV()))
        }
    }

    @Test
    fun loweringOwnsItsCopyAndPreservesCallerBufferRangeAndOrder() {
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(fragment("void main(){result=texture(image,coordinates);}"), "owned.frag", ShaderStage.Fragment)
            val original = words(shader.spirV())
            val ranged = ByteBuffer.allocate(original.size * 4 + 8).order(ByteOrder.LITTLE_ENDIAN)
            ranged.putInt(123)
            for (word in original) ranged.putInt(word)
            ranged.putInt(456)
            (ranged as java.nio.Buffer).position(4)
            (ranged as java.nio.Buffer).limit(original.size * 4 + 4)
            ranged.order(ByteOrder.BIG_ENDIAN)
            val lowered = lowerOpenGLSampleCoordinates(ranged, shader.stage)
            assertEquals(4, ranged.position())
            assertEquals(original.size * 4 + 4, ranged.limit())
            assertEquals(ByteOrder.BIG_ENDIAN, ranged.order())
            assertSampleAdaptation(original, words(lowered))
            lowered.putInt(0, 0)
            assertContentEquals(original, words(ranged))
            assertContentEquals(original, words(shader.spirV()))
        }
    }

    @Test
    fun realFrontendAttachmentReadsRejectExplicitlyWhileUnusedSourceExtensionsRemainHarmless() {
        val extension = "#extension GL_EXT_shader_tile_image : require\n"
        GlslCompiler().use { compiler ->
            val bodies = listOf(
                "void main(){result=vec4(depthAttachmentReadEXT());}",
                "void main(){result=vec4(stencilAttachmentReadEXT());}",
                "layout(location=0) tileImageEXT attachmentEXT color0;\nvoid main(){result=colorAttachmentReadEXT(color0);}",
            )
            for (body in bodies) {
                val declarationsAndBody = body.replace("void main", "layout(location=0) out vec4 result;\nvoid main")
                val text = "#version 450\n$extension$declarationsAndBody"
                val shader = compiler.compile(text, "attachment.frag", ShaderStage.Fragment)
                val original = words(shader.spirV())
                val failure = assertFailsWith<IllegalArgumentException> { lowerOpenGLTarget(shader, 140) }
                assertContains(failure.message!!, "Canonical image operation")
                assertContentEquals(original, words(shader.spirV()))
            }
            val unusedAttachment = compiler.compile(
                "#version 450\n${extension}layout(location=0) tileImageEXT attachmentEXT color0;\nlayout(location=0) out vec4 result;\nvoid main(){result=vec4(1);}",
                "unused-attachment.frag", ShaderStage.Fragment,
            )
            assertContentEquals(words(unusedAttachment.spirV()), words(lowerOpenGLSampleCoordinates(unusedAttachment.spirV(), unusedAttachment.stage)))
            lowerOpenGLTarget(unusedAttachment, 140)
            val harmless = compiler.compile(fragment("void main(){result=vec4(1);}").replace("#version 450", "#version 450\n$extension"), "unused-extension.frag", ShaderStage.Fragment)
            assertContentEquals(words(harmless.spirV()), words(lowerOpenGLSampleCoordinates(harmless.spirV(), harmless.stage)))
            lowerOpenGLTarget(harmless, 140)
        }
    }

    private fun assertSampleAdaptation(original: IntArray, lowered: IntArray) {
        val before = instructions(original)
        val after = instructions(lowered).toMutableList()
        val samples = before.filter { opcode(it) == SpvOpImageSampleImplicitLod }
        assertTrue(samples.isNotEmpty())
        assertEquals(original[3] + 1 + 4 * samples.size, lowered[3])
        val constant = after.single { opcode(it) == SpvOpConstant && it[2] == original[3] }
        assertEquals(0x3f800000, constant[3])
        val functionIndex = after.indexOfFirst { opcode(it) == SpvOpFunction }
        assertTrue(after.indexOf(constant) < functionIndex)
        after.remove(constant)
        val newIds = mutableSetOf(original[3])
        for (sample in samples) {
            val index = after.indexOfFirst { opcode(it) == SpvOpImageSampleImplicitLod && it[2] == sample[2] }
            val extractHorizontal = after[index - 4]
            val extractVertical = after[index - 3]
            val subtract = after[index - 2]
            val construct = after[index - 1]
            assertEquals(SpvOpCompositeExtract, opcode(extractHorizontal))
            assertEquals(SpvOpCompositeExtract, opcode(extractVertical))
            assertEquals(sample[4], extractHorizontal[3]); assertEquals(0, extractHorizontal[4])
            assertEquals(sample[4], extractVertical[3]); assertEquals(1, extractVertical[4])
            assertEquals(SpvOpFSub, opcode(subtract))
            assertEquals(original[3], subtract[3]); assertEquals(extractVertical[2], subtract[4])
            assertEquals(SpvOpCompositeConstruct, opcode(construct))
            assertEquals(extractHorizontal[2], construct[3]); assertEquals(subtract[2], construct[4])
            for (instruction in listOf(extractHorizontal, extractVertical, subtract, construct)) {
                assertTrue(instruction[2] >= original[3] && instruction[2] < lowered[3])
                assertTrue(newIds.add(instruction[2]))
            }
            assertEquals(construct[2], after[index][4])
            after[index][4] = sample[4]
            repeat(4) { after.removeAt(index - 4) }
        }
        assertEquals(before.size, after.size)
        before.zip(after).forEach { (expected, actual) -> assertContentEquals(expected, actual) }
        assertEquals(lowered[3] - original[3], newIds.size)
    }

    private fun fragment(body: String, extra: String = "") = """
        #version 450
        layout(set=0,binding=0) uniform sampler2D image;
        layout(location=0) in vec2 coordinates;
        layout(location=0) out vec4 result;
        $extra
        $body
    """.trimIndent()
    private fun words(buffer: ByteBuffer) = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().let { source ->
        IntArray(source.remaining()).also { source.get(it) }
    }
    private fun buffer(words: IntArray) = ByteBuffer.allocateDirect(words.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { it.asIntBuffer().put(words) }
    private fun opcode(instruction: IntArray) = instruction[0] and 0xffff
    private fun opcodeAt(words: IntArray, offset: Int) = words[offset] and 0xffff
    private fun offsets(words: IntArray): List<Int> {
        val result = mutableListOf<Int>()
        var offset = 5
        while (offset < words.size) { result += offset; offset += words[offset] ushr 16 }
        return result
    }
    private fun instructions(words: IntArray) = offsets(words).map { offset -> words.copyOfRange(offset, offset + (words[offset] ushr 16)) }
    private fun replaceSample(words: IntArray, replacement: (IntArray) -> IntArray): IntArray {
        val result = words.take(5).toMutableList()
        for (instruction in instructions(words)) result.addAll((if (opcode(instruction) == SpvOpImageSampleImplicitLod) replacement(instruction) else instruction).toList())
        return result.toIntArray()
    }
}
