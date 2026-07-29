/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.codegen

internal fun NativeStructDefinition.render(layout: NativeStructLayout = layout()): String = buildString {
    appendLine("// Generated. Do not edit.")
    appendLine("@file:Suppress(\"NOTHING_TO_INLINE\")")
    appendLine()
    appendLine("package $packageName")
    val imports = buildList {
        if (packageName != MEMORY_PACKAGE) {
            add("$MEMORY_PACKAGE.MemoryFrame")
            add("$MEMORY_PACKAGE.NativeAddress")
            if (!isGpuBufferData && pushConstantBlock == null) add("$MEMORY_PACKAGE.MemoryStack")
        }
        if (isGpuBufferData && packageName != RENDER_PACKAGE) {
            add("$RENDER_PACKAGE.CommandEncoder")
            add("$RENDER_PACKAGE.ShaderField")
            add("$RENDER_PACKAGE.ShaderValueType")
            add("$RENDER_PACKAGE.UniformBinding")
            add("$RENDER_PACKAGE.UniformBufferLayout")
        }
        if (pushConstantBlock != null && packageName != RENDER_PACKAGE) {
            add("$RENDER_PACKAGE.PushConstantField")
            add("$RENDER_PACKAGE.PushConstantLayout")
            add("$RENDER_PACKAGE.RenderPass")
            add("$RENDER_PACKAGE.ShaderStage")
            add("$RENDER_PACKAGE.ShaderValueType")
        }
        if ((isGpuBufferData || pushConstantBlock != null) && fields.any { it.gpuValueType() == GpuValueType.Matrix4 } && packageName != RENDER_PACKAGE) add("$RENDER_PACKAGE.Matrix4")
    }
    if (imports.isNotEmpty()) {
        appendLine()
        imports.sorted().forEach { appendLine("import $it") }
    }
    appendLine()
    appendLine("@PublishedApi")
    appendLine("internal object $memoryLayoutName {")
    layout.fields.forEach { appendLine("    const val ${it.field.constantName}_OFFSET = ${it.offset}") }
    pushConstantBlock?.let { block ->
        val destinationOffsetBytes = block.offsetBytes
        val stages = block.stages.joinToString { "ShaderStage.${it.sourceName}" }
        appendLine("    const val DESTINATION_OFFSET_BYTES = $destinationOffsetBytes")
        appendLine("    const val DESTINATION_END_OFFSET_BYTES = ${Math.addExact(destinationOffsetBytes, layout.size)}")
        layout.fields.forEach { appendLine("    const val ${it.field.constantName}_DESTINATION_OFFSET_BYTES = ${Math.addExact(destinationOffsetBytes, it.offset)}") }
        appendLine()
        appendLine("    val Stages = setOf($stages)")
    }
    appendLine()
    appendLine("    const val SIZE = ${layout.size}")
    appendLine("    const val ALIGNMENT = ${layout.alignment}")
    appendLine("}")
    when {
        isGpuBufferData -> {
            appendGpuBufferLayout(this@render, layout)
            appendShaderDataWriter(this@render, layout)
            appendGpuBufferWriteFunction(this@render, layout)
        }

        pushConstantBlock != null -> {
            appendPushConstantLayout(this@render, layout)
            appendShaderDataWriter(this@render, layout)
            appendPushConstantFunction(this@render, layout)
        }

        else -> {
            appendLine()
            appendFunctionHeader(this@render, "MemoryStack")
            appendLine("): R = frame { $allocFunctionName(block) }")
            appendLine()
            appendFunctionHeader(this@render, "MemoryFrame")
            appendLine("): R {")
            appendLine("    val base = reserve(")
            appendLine("        $memoryLayoutName.SIZE,")
            appendLine("        $memoryLayoutName.ALIGNMENT,")
            appendLine("    )")
            appendLine()
            appendLine("    return block(")
            fields.forEach { appendLine("        base + $memoryLayoutName.${it.constantName}_OFFSET,") }
            appendLine("    )")
            appendLine("}")
        }
    }
}

private fun StringBuilder.appendGpuBufferLayout(definition: NativeStructDefinition, layout: NativeStructLayout) = with(definition) {
    appendLine()
    appendLine("${visibilityPrefix()}val $gpuLayoutName = UniformBufferLayout(")
    appendLine("    $memoryLayoutName.SIZE,")
    appendLine("    listOf(")
    layout.fields.forEach { field -> appendLine("        ShaderField(\"${field.field.name}\", ShaderValueType.${field.field.gpuValueType().sourceName}, $memoryLayoutName.${field.field.constantName}_OFFSET),") }
    appendLine("    ),")
    appendLine(")")
}

private fun StringBuilder.appendPushConstantLayout(definition: NativeStructDefinition, layout: NativeStructLayout) = with(definition) {
    appendLine()
    appendLine("${visibilityPrefix()}val $gpuLayoutName = PushConstantLayout(")
    appendLine("    $memoryLayoutName.DESTINATION_END_OFFSET_BYTES,")
    appendLine("    listOf(")
    layout.fields.forEach { field ->
        appendLine("        PushConstantField(\"${field.field.name}\", ShaderValueType.${field.field.gpuValueType().sourceName}, $memoryLayoutName.${field.field.constantName}_DESTINATION_OFFSET_BYTES, $memoryLayoutName.Stages),")
    }
    appendLine("    ),")
    appendLine(")")
}

private fun StringBuilder.appendShaderDataWriter(definition: NativeStructDefinition, layout: NativeStructLayout) = with(definition) {
    appendLine()
    appendLine("@JvmInline")
    appendLine("${visibilityPrefix()}value class $gpuWriterName @PublishedApi internal constructor(private val baseAddress: NativeAddress) {")
    layout.fields.forEachIndexed { index, field ->
        if (index > 0) appendLine()
        appendShaderDataWriterField(definition, field.field)
    }
    appendLine("}")
}

private fun StringBuilder.appendShaderDataWriterField(definition: NativeStructDefinition, field: NativeStructField) = with(definition) {
    val address = "baseAddress + $memoryLayoutName.${field.constantName}_OFFSET"
    when (field.gpuValueType()) {
        GpuValueType.Int -> appendShaderDataWriterProperty(field.name, "Int", "memoryFrame.storeInt($address, value)")
        GpuValueType.Float -> appendShaderDataWriterProperty(field.name, "Float", "memoryFrame.storeFloat($address, value)")
        GpuValueType.Float2 -> appendShaderDataWriterFunction(field.name, 2, "memoryFrame.storeFloat2($address, x, y)")
        GpuValueType.Float3 -> appendShaderDataWriterFunction(field.name, 3, "memoryFrame.storeFloat3($address, x, y, z)")
        GpuValueType.Float4 -> appendShaderDataWriterFunction(field.name, 4, "memoryFrame.storeFloat4($address, x, y, z, w)")
        GpuValueType.Matrix4 -> appendShaderDataWriterProperty(field.name, "Matrix4", "value.writeColumnMajor(memoryFrame, $address)")
    }
}

private fun StringBuilder.appendShaderDataWriterProperty(name: String, type: String, store: String) {
    appendLine("    context(memoryFrame: MemoryFrame)")
    appendLine("    var $name: $type")
    appendLine("        get() = error(\"Shader data writer properties are write-only\")")
    appendLine("        set(value) = $store")
}

private fun StringBuilder.appendShaderDataWriterFunction(name: String, componentCount: Int, store: String) {
    val componentNames = listOf("x", "y", "z", "w").take(componentCount)
    appendLine("    context(memoryFrame: MemoryFrame)")
    appendLine("    fun $name(${componentNames.joinToString { "$it: Float" }}) {")
    appendLine("        $store")
    appendLine("    }")
}

private fun StringBuilder.appendGpuBufferWriteFunction(definition: NativeStructDefinition, layout: NativeStructLayout) = with(definition) {
    appendLine()
    appendLine("${visibilityPrefix()}inline fun CommandEncoder.$writeFunctionName(")
    appendLine("    write: context(MemoryFrame) $gpuWriterName.() -> Unit,")
    appendLine("): UniformBinding {")
    appendLine("    return memoryStack.frame {")
    appendLine("        val base = reserve($memoryLayoutName.SIZE, $memoryLayoutName.ALIGNMENT)")
    layout.paddingRanges().forEach { appendLine("        clear(base + ${it.offset}, ${it.byteCount})") }
    appendLine("        $gpuWriterName(base).write()")
    appendLine("        this@$writeFunctionName.writeUniform($gpuLayoutName, base)")
    appendLine("    }")
    appendLine("}")
}

private fun StringBuilder.appendPushConstantFunction(definition: NativeStructDefinition, layout: NativeStructLayout) = with(definition) {
    appendLine()
    appendLine("${visibilityPrefix()}inline fun RenderPass.$pushFunctionName(")
    appendLine("    write: context(MemoryFrame) $gpuWriterName.() -> Unit,")
    appendLine(") {")
    appendLine("    memoryStack.frame {")
    appendLine("        val base = reserve($memoryLayoutName.SIZE, $memoryLayoutName.ALIGNMENT)")
    layout.paddingRanges().forEach { appendLine("        clear(base + ${it.offset}, ${it.byteCount})") }
    appendLine("        $gpuWriterName(base).write()")
    appendLine("        this@$pushFunctionName.pushConstants($memoryLayoutName.Stages, base, $memoryLayoutName.SIZE, $memoryLayoutName.DESTINATION_OFFSET_BYTES)")
    appendLine("    }")
    appendLine("}")
}

private fun StringBuilder.appendFunctionHeader(definition: NativeStructDefinition, receiverType: String) {
    appendLine("${definition.visibilityPrefix()}inline fun <R> $receiverType.${definition.allocFunctionName}(")
    appendLine("    block: MemoryFrame.(")
    definition.fields.forEach { appendLine("        ${it.name}: NativeAddress,") }
    appendLine("    ) -> R,")
}

private fun NativeStructDefinition.visibilityPrefix(): String = visibility.takeIf(String::isNotEmpty)?.plus(' ') ?: ""

private fun NativeStructLayout.paddingRanges(): List<NativeStructPadding> {
    val padding = mutableListOf<NativeStructPadding>()
    var endOffset = 0
    for (field in fields) {
        if (field.offset > endOffset) padding += NativeStructPadding(endOffset, field.offset - endOffset)
        endOffset = Math.addExact(field.offset, field.field.byteCount)
    }
    if (size > endOffset) padding += NativeStructPadding(endOffset, size - endOffset)
    return padding
}

private data class NativeStructPadding(val offset: Int, val byteCount: Int)

private const val MEMORY_PACKAGE = "heckerpowered.render.memory"
private const val RENDER_PACKAGE = "heckerpowered.render"
