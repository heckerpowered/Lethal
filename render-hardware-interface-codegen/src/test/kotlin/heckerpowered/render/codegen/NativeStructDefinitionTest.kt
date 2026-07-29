/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeStructDefinitionTest {
    @Test
    fun layoutAlignsEveryFieldAndRoundsTheCompleteStructure() {
        val layout = UploadScratch.layout()

        assertEquals(expected = listOf(0, 64, 80, 96), actual = layout.fields.map(NativeStructLayoutField::offset))
        assertEquals(expected = 352, actual = layout.size)
        assertEquals(expected = 16, actual = layout.alignment)
    }

    @Test
    fun minimumStructureAlignmentRoundsStd140DataWithoutPaddingFields() {
        val layout = UniformData.layout()

        assertEquals(expected = listOf(0, 64), actual = layout.fields.map(NativeStructLayoutField::offset))
        assertEquals(expected = 80, actual = layout.size)
        assertEquals(expected = 16, actual = layout.alignment)
    }

    @Test
    fun sourceMatchesTheNativeStructContract() {
        val expected = """
            // Generated. Do not edit.
            @file:Suppress("NOTHING_TO_INLINE")

            package your.package

            import heckerpowered.render.memory.MemoryFrame
            import heckerpowered.render.memory.MemoryStack
            import heckerpowered.render.memory.NativeAddress

            @PublishedApi
            internal object UploadScratchLayout {
                const val MATRIX_OFFSET = 0
                const val VIEWPORT_OFFSET = 64
                const val COLOR_OFFSET = 80
                const val SCRATCH_OFFSET = 96

                const val SIZE = 352
                const val ALIGNMENT = 16
            }

            internal inline fun <R> MemoryStack.allocUploadScratch(
                block: MemoryFrame.(
                    matrix: NativeAddress,
                    viewport: NativeAddress,
                    color: NativeAddress,
                    scratch: NativeAddress,
                ) -> R,
            ): R = frame { allocUploadScratch(block) }

            internal inline fun <R> MemoryFrame.allocUploadScratch(
                block: MemoryFrame.(
                    matrix: NativeAddress,
                    viewport: NativeAddress,
                    color: NativeAddress,
                    scratch: NativeAddress,
                ) -> R,
            ): R {
                val base = reserve(
                    UploadScratchLayout.SIZE,
                    UploadScratchLayout.ALIGNMENT,
                )

                return block(
                    base + UploadScratchLayout.MATRIX_OFFSET,
                    base + UploadScratchLayout.VIEWPORT_OFFSET,
                    base + UploadScratchLayout.COLOR_OFFSET,
                    base + UploadScratchLayout.SCRATCH_OFFSET,
                )
            }
        """.trimIndent() + '\n'

        assertEquals(expected = expected, actual = UploadScratch.render())
    }

    @Test
    fun pushConstantBlockGeneratesTypedRenderPassCommand() {
        val expected = """
            // Generated. Do not edit.
            @file:Suppress("NOTHING_TO_INLINE")

            package your.package

            import heckerpowered.render.PushConstantField
            import heckerpowered.render.PushConstantLayout
            import heckerpowered.render.RenderPass
            import heckerpowered.render.ShaderStage
            import heckerpowered.render.ShaderValueType
            import heckerpowered.render.memory.MemoryFrame
            import heckerpowered.render.memory.NativeAddress

            @PublishedApi
            internal object DrawConstantsMemoryLayout {
                const val POSITION_OFFSET = 0
                const val COLOR_OFFSET = 16
                const val DESTINATION_OFFSET_BYTES = 16
                const val DESTINATION_END_OFFSET_BYTES = 48
                const val POSITION_DESTINATION_OFFSET_BYTES = 16
                const val COLOR_DESTINATION_OFFSET_BYTES = 32

                val Stages = setOf(ShaderStage.Vertex)

                const val SIZE = 32
                const val ALIGNMENT = 16
            }

            internal val DrawConstantsLayout = PushConstantLayout(
                DrawConstantsMemoryLayout.DESTINATION_END_OFFSET_BYTES,
                listOf(
                    PushConstantField("position", ShaderValueType.Float3, DrawConstantsMemoryLayout.POSITION_DESTINATION_OFFSET_BYTES, DrawConstantsMemoryLayout.Stages),
                    PushConstantField("color", ShaderValueType.Float4, DrawConstantsMemoryLayout.COLOR_DESTINATION_OFFSET_BYTES, DrawConstantsMemoryLayout.Stages),
                ),
            )

            @JvmInline
            internal value class DrawConstantsWriter @PublishedApi internal constructor(private val baseAddress: NativeAddress) {
                context(memoryFrame: MemoryFrame)
                fun position(x: Float, y: Float, z: Float) {
                    memoryFrame.storeFloat3(baseAddress + DrawConstantsMemoryLayout.POSITION_OFFSET, x, y, z)
                }

                context(memoryFrame: MemoryFrame)
                fun color(x: Float, y: Float, z: Float, w: Float) {
                    memoryFrame.storeFloat4(baseAddress + DrawConstantsMemoryLayout.COLOR_OFFSET, x, y, z, w)
                }
            }

            internal inline fun RenderPass.pushDrawConstants(
                write: context(MemoryFrame) DrawConstantsWriter.() -> Unit,
            ) {
                memoryStack.frame {
                    val base = reserve(DrawConstantsMemoryLayout.SIZE, DrawConstantsMemoryLayout.ALIGNMENT)
                    clear(base + 12, 4)
                    DrawConstantsWriter(base).write()
                    this@pushDrawConstants.pushConstants(DrawConstantsMemoryLayout.Stages, base, DrawConstantsMemoryLayout.SIZE, DrawConstantsMemoryLayout.DESTINATION_OFFSET_BYTES)
                }
            }
        """.trimIndent() + '\n'

        assertEquals(expected = expected, actual = DrawConstants.render())
    }

    @Test
    fun pushConstantBlockRejectsAByteSizeVulkanCannotRecord() {
        val definition = NativeStructDefinition("your.package", "InvalidConstants", "internal", listOf(NativeStructField("data", "DATA", NativeElementType.Byte, 3)), pushConstantBlock = PushConstantBlockDefinition(0, listOf(GpuShaderStage.Fragment)))
        val exception = assertFailsWith<IllegalArgumentException> { definition.layout() }

        assertEquals(expected = "InvalidConstants push-constant size must be four-byte aligned", actual = exception.message)
    }

    @Test
    fun gpuBufferDataGeneratesLayoutAndSetterCommand() {
        val expected = """
            // Generated. Do not edit.
            @file:Suppress("NOTHING_TO_INLINE")

            package your.package

            import heckerpowered.render.CommandEncoder
            import heckerpowered.render.Matrix4
            import heckerpowered.render.ShaderField
            import heckerpowered.render.ShaderValueType
            import heckerpowered.render.UniformBinding
            import heckerpowered.render.UniformBufferLayout
            import heckerpowered.render.memory.MemoryFrame
            import heckerpowered.render.memory.NativeAddress

            @PublishedApi
            internal object UniformDataMemoryLayout {
                const val MATRIX_OFFSET = 0
                const val VIEWPORT_OFFSET = 64

                const val SIZE = 80
                const val ALIGNMENT = 16
            }

            internal val UniformDataLayout = UniformBufferLayout(
                UniformDataMemoryLayout.SIZE,
                listOf(
                    ShaderField("matrix", ShaderValueType.Matrix4, UniformDataMemoryLayout.MATRIX_OFFSET),
                    ShaderField("viewport", ShaderValueType.Float2, UniformDataMemoryLayout.VIEWPORT_OFFSET),
                ),
            )

            @JvmInline
            internal value class UniformDataWriter @PublishedApi internal constructor(private val baseAddress: NativeAddress) {
                context(memoryFrame: MemoryFrame)
                var matrix: Matrix4
                    get() = error("Shader data writer properties are write-only")
                    set(value) = value.writeColumnMajor(memoryFrame, baseAddress + UniformDataMemoryLayout.MATRIX_OFFSET)

                context(memoryFrame: MemoryFrame)
                fun viewport(x: Float, y: Float) {
                    memoryFrame.storeFloat2(baseAddress + UniformDataMemoryLayout.VIEWPORT_OFFSET, x, y)
                }
            }

            internal inline fun CommandEncoder.writeUniformData(
                write: context(MemoryFrame) UniformDataWriter.() -> Unit,
            ): UniformBinding {
                return memoryStack.frame {
                    val base = reserve(UniformDataMemoryLayout.SIZE, UniformDataMemoryLayout.ALIGNMENT)
                    clear(base + 72, 8)
                    UniformDataWriter(base).write()
                    this@writeUniformData.writeUniform(UniformDataLayout, base)
                }
            }
        """.trimIndent() + '\n'

        assertEquals(expected = expected, actual = UniformData.render())
    }

    private companion object {
        val UploadScratch = NativeStructDefinition(
            "your.package",
            "UploadScratch",
            "internal",
            listOf(
                NativeStructField("matrix", "MATRIX", NativeElementType.Float, 16),
                NativeStructField("viewport", "VIEWPORT", NativeElementType.Float, 4),
                NativeStructField("color", "COLOR", NativeElementType.Float, 4),
                NativeStructField("scratch", "SCRATCH", NativeElementType.Byte, 256, 16),
            ),
        )

        val DrawConstants = NativeStructDefinition(
            "your.package",
            "DrawConstants",
            "internal",
            listOf(
                NativeStructField("position", "POSITION", NativeElementType.Float, 3, GpuValueType.Float3.alignment),
                NativeStructField("color", "COLOR", NativeElementType.Float, 4, GpuValueType.Float4.alignment),
            ),
            pushConstantBlock = PushConstantBlockDefinition(16, listOf(GpuShaderStage.Vertex)),
        )

        val UniformData = NativeStructDefinition(
            "your.package",
            "UniformData",
            "internal",
            listOf(
                NativeStructField("matrix", "MATRIX", NativeElementType.Float, 16, GpuValueType.Matrix4.alignment),
                NativeStructField("viewport", "VIEWPORT", NativeElementType.Float, 2, GpuValueType.Float2.alignment),
            ),
            minimumAlignment = 16,
            isGpuBufferData = true,
        )
    }
}
