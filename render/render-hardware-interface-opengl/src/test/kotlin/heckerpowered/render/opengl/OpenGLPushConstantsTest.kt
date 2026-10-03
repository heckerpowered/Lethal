/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.opengl.shader.OpenGLProgramPushConstant
import heckerpowered.render.opengl.shader.OpenGLPushConstantMember
import heckerpowered.render.pipeline.PushConstantLayout
import heckerpowered.render.pipeline.PushConstantRange
import heckerpowered.render.shader.ShaderStage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class OpenGLPushConstantsTest {
    @Test
    fun partialWritesInitializeOnlyTheirAddressedBytes() {
        val values = OpenGLPushConstants()
        val layout = layout()
        val color = member()
        values.write(layout, setOf(ShaderStage.Fragment), 0, bytes(1f, 2f))
        assertFailsWith<IllegalStateException> { values.valuesFor(layout, color) }
        values.write(layout, setOf(ShaderStage.Fragment), 8, bytes(3f, 4f))
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), values.valuesFor(layout, color))
        values.write(layout, setOf(ShaderStage.Fragment), 4, bytes(5f))
        assertContentEquals(floatArrayOf(1f, 5f, 3f, 4f), values.valuesFor(layout, color))
    }

    @Test
    fun snapshotPreservesSelectedRawBytesAndDoesNotConsumeTheSource() {
        val values = OpenGLPushConstants()
        val source = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder())
        val bits = intArrayOf(0x7FA12345, Int.MIN_VALUE, 0xFF800000.toInt(), 0x3F800000)
        source.putInt(0, 77)
        bits.forEachIndexed { index, word -> source.putInt(4 + index * 4, word) }
        source.position(4).limit(20)
        values.write(layout(), setOf(ShaderStage.Fragment), 0, source)
        assertEquals(4, source.position())
        assertEquals(20, source.limit())
        source.putInt(4, 0)
        assertContentEquals(bits, values.valuesFor(layout(), member()).map { it.toRawBits() }.toIntArray())
    }

    @Test
    fun compatibleLayoutsReuseValuesAndSwitchesAloneDoNotEraseThem() {
        val values = OpenGLPushConstants()
        val original = layout()
        values.write(original, setOf(ShaderStage.Fragment), 0, bytes(1f, 2f, 3f, 4f))
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), values.valuesFor(layout(), member()))
        val different = layout(size = 32)
        assertFailsWith<IllegalStateException> { values.valuesFor(different, member()) }
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), values.valuesFor(original, member()))
    }

    @Test
    fun incompatibleWriteReplacesOnlyItsStageBytesUntilTheyAreRewritten() {
        val values = OpenGLPushConstants()
        val both = setOf(ShaderStage.Vertex, ShaderStage.Fragment)
        val original = layout(stages = both)
        val different = layout()
        values.write(original, both, 0, bytes(1f, 2f, 3f, 4f))
        values.write(different, setOf(ShaderStage.Fragment), 0, bytes(9f))
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), values.valuesFor(original, member(stage = ShaderStage.Vertex)))
        assertFailsWith<IllegalStateException> { values.valuesFor(original, member()) }
        assertFailsWith<IllegalStateException> { values.valuesFor(different, member()) }
        values.write(original, both, 0, bytes(7f))
        assertContentEquals(floatArrayOf(7f, 2f, 3f, 4f), values.valuesFor(original, member()))
    }

    @Test
    fun overlapRequiresEveryExposedStageAndRejectedWritesLeaveHistoryIntact() {
        val values = OpenGLPushConstants()
        val both = setOf(ShaderStage.Vertex, ShaderStage.Fragment)
        val layout = layout(stages = both)
        values.write(layout, both, 0, bytes(1f, 2f, 3f, 4f))
        assertFailsWith<IllegalArgumentException> { values.write(layout, setOf(ShaderStage.Vertex), 4, bytes(9f)) }
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), values.valuesFor(layout, member()))
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), values.valuesFor(layout, member(stage = ShaderStage.Vertex)))
    }

    @Test
    fun differingStageIntervalsAcceptWritesSplitAtTheirOverlap() {
        val values = OpenGLPushConstants()
        val layout = PushConstantLayout(listOf(
            PushConstantRange(
                setOf(ShaderStage.Vertex),
                0,
                16,
            ),
            PushConstantRange(
                setOf(ShaderStage.Fragment),
                8,
                16,
            ),
        ))
        values.write(layout, setOf(ShaderStage.Vertex), 0, bytes(1f, 2f))
        values.write(layout, setOf(ShaderStage.Vertex, ShaderStage.Fragment), 8, bytes(3f, 4f))
        values.write(layout, setOf(ShaderStage.Fragment), 16, bytes(5f, 6f))
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), values.valuesFor(layout, member(stage = ShaderStage.Vertex)))
        assertContentEquals(floatArrayOf(3f, 4f, 5f, 6f), values.valuesFor(layout, member(offset = 8)))
    }

    @Test
    fun matrixStridePaddingNeedsNoInitializationAndValuesAreColumnMajor() {
        for (rowMajor in listOf(false, true)) {
            val values = OpenGLPushConstants()
            val layout = layout(offset = 48, size = 112)
            for (vector in 0 until 4) {
                values.write(layout, setOf(ShaderStage.Fragment), 48 + vector * 32,
                    bytes(*FloatArray(4) { component -> (vector * 4 + component).toFloat() }))
            }
            val matrix = OpenGLProgramPushConstant(
                ShaderStage.Fragment,
                OpenGLPushConstantMember(
                    48,
                    4,
                    4,
                    32,
                    rowMajor,
                    "matrix",
                ),
                UniformLocation(2),
            )
            val expected = FloatArray(16) { index -> if (rowMajor) ((index % 4) * 4 + index / 4).toFloat() else index.toFloat() }
            assertContentEquals(expected, values.valuesFor(layout, matrix))
        }
    }

    @Test
    fun absoluteHighOffsetDoesNotRequireAnAddressSpaceSizedAllocation() {
        val offset = Int.MAX_VALUE - 19
        val layout = layout(offset = offset)
        val values = OpenGLPushConstants()
        values.write(layout, setOf(ShaderStage.Fragment), offset, bytes(1f, 2f, 3f, 4f))
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), values.valuesFor(layout, member(offset = offset)))
    }

    @Test
    fun invalidWritesAndMemberAccessesAreRejected() {
        val values = OpenGLPushConstants()
        val layout = layout()
        assertFailsWith<IllegalArgumentException> { values.write(layout, emptySet(), 0, bytes(1f)) }
        assertFailsWith<IllegalArgumentException> { values.write(layout, setOf(ShaderStage.Fragment), 0, ByteBuffer.allocate(0)) }
        assertFailsWith<IllegalArgumentException> { values.write(layout, setOf(ShaderStage.Fragment), 2, bytes(1f)) }
        assertFailsWith<IllegalArgumentException> { values.write(layout, setOf(ShaderStage.Fragment), 16, bytes(1f)) }
        assertFailsWith<IllegalArgumentException> { values.write(layout, setOf(ShaderStage.Fragment), 0, ByteBuffer.allocate(3)) }
        assertFailsWith<IllegalArgumentException> { values.valuesFor(layout, member(stage = ShaderStage.Vertex)) }
        assertFailsWith<IllegalArgumentException> { values.valuesFor(layout, member(offset = 4)) }
        assertFailsWith<IllegalStateException> { values.valuesFor(layout, member()) }
    }

    private fun layout(offset: Int = 0, size: Int = 16, stages: Set<ShaderStage> = setOf(ShaderStage.Fragment)): PushConstantLayout =
        PushConstantLayout(listOf(PushConstantRange(
            stages,
            offset,
            size,
        )))

    private fun member(offset: Int = 0, stage: ShaderStage = ShaderStage.Fragment): OpenGLProgramPushConstant = OpenGLProgramPushConstant(
        stage,
        OpenGLPushConstantMember(
            offset,
            4,
            1,
            0,
            false,
            "color",
        ),
        UniformLocation(9),
    )

    private fun bytes(vararg values: Float): ByteBuffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.nativeOrder()).apply {
        values.forEach { putFloat(it) }
        flip()
    }
}
