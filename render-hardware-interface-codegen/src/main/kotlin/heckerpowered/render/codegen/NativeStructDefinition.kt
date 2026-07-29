/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.codegen

internal data class NativeStructDefinition(
    val packageName: String,
    val typeName: String,
    val visibility: String,
    val fields: List<NativeStructField>,
    val minimumAlignment: Int = 1,
    val pushConstantBlock: PushConstantBlockDefinition? = null,
    val isGpuBufferData: Boolean = false,
) {
    val allocFunctionName: String
        get() = "alloc$typeName"

    val memoryLayoutName: String
        get() = if (isGpuBufferData || pushConstantBlock != null) "${typeName}MemoryLayout" else "${typeName}Layout"

    val gpuLayoutName: String
        get() = "${typeName}Layout"

    val gpuWriterName: String
        get() = "${typeName}Writer"

    val writeFunctionName: String
        get() = "write$typeName"

    val pushFunctionName: String
        get() = "push$typeName"
}

internal data class PushConstantBlockDefinition(
    val offsetBytes: Int,
    val stages: List<GpuShaderStage>,
)

internal data class NativeStructField(
    val name: String,
    val constantName: String,
    val elementType: NativeElementType,
    val elementCount: Int,
    val alignment: Int = elementType.byteCount,
) {
    val byteCount: Int = Math.multiplyExact(elementCount, elementType.byteCount)

    fun gpuValueType(): GpuValueType {
        return when (elementType) {
            NativeElementType.Int -> if (elementCount == 1) GpuValueType.Int else null
            NativeElementType.Float -> when (elementCount) {
                1 -> GpuValueType.Float
                2 -> GpuValueType.Float2
                3 -> GpuValueType.Float3
                4 -> GpuValueType.Float4
                16 -> GpuValueType.Matrix4
                else -> null
            }

            NativeElementType.Byte -> null
        } ?: error("$name does not have a shader value type")
    }
}

internal enum class NativeElementType(val byteCount: Int) {
    // TODO: Use typealias
    Byte(kotlin.Byte.SIZE_BYTES),
    Float(kotlin.Float.SIZE_BYTES),
    Int(kotlin.Int.SIZE_BYTES),
}

internal enum class GpuValueType(val sourceName: String, val alignment: Int) {
    Int("Int", kotlin.Int.SIZE_BYTES),
    Float("Float", kotlin.Float.SIZE_BYTES),
    Float2("Float2", kotlin.Float.SIZE_BYTES * 2),
    Float3("Float3", kotlin.Float.SIZE_BYTES * 4),
    Float4("Float4", kotlin.Float.SIZE_BYTES * 4),
    Matrix4("Matrix4", kotlin.Float.SIZE_BYTES * 4),
}

internal enum class GpuShaderStage(val sourceName: String) {
    Vertex("Vertex"),
    Fragment("Fragment"),
}

internal data class NativeStructLayout(
    val fields: List<NativeStructLayoutField>,
    val size: Int,
    val alignment: Int,
)

internal data class NativeStructLayoutField(
    val field: NativeStructField,
    val offset: Int,
)

internal fun NativeStructDefinition.layout(): NativeStructLayout {
    var offset = 0L
    val layoutFields = fields.map { field ->
        offset = alignUp(offset, field.alignment)
        require(offset <= Int.MAX_VALUE) { "$typeName requires more than ${Int.MAX_VALUE} bytes" }
        NativeStructLayoutField(field, offset.toInt()).also { offset = Math.addExact(offset, field.byteCount.toLong()) }
    }
    val structureAlignment = maxOf(minimumAlignment, fields.maxOf(NativeStructField::alignment))
    val size = alignUp(offset, structureAlignment)
    require(size <= Int.MAX_VALUE) { "$typeName requires more than ${Int.MAX_VALUE} bytes" }
    if (pushConstantBlock != null) {
        require(size % Int.SIZE_BYTES == 0L) { "$typeName push-constant size must be four-byte aligned" }
        Math.addExact(pushConstantBlock.offsetBytes, size.toInt())
    }
    return NativeStructLayout(layoutFields, size.toInt(), structureAlignment)
}

private fun alignUp(value: Long, alignment: Int): Long = Math.addExact(value, alignment - 1L) and -alignment.toLong()
