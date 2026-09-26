/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.codegen

/** Emits constant offsets and thin accessors, not a runtime layout interpreter. */
internal fun renderStruct(schema: StructSchema, layout: StructLayout): String = buildString {
    appendLine("/*")
    appendLine(" * SPDX-License-Identifier: MIT")
    appendLine(" * Copyright (c) 2026 heckerpowered")
    appendLine(" */")
    appendLine()
    appendLine("// Generated. Do not edit.")
    appendLine("@file:Suppress(\"NOTHING_TO_INLINE\")")
    appendLine()
    if (schema.packageName.isNotEmpty()) appendLine("package ${schema.packageName.split('.').joinToString(".") { quoted(it) }}")
    appendLine()
    appendLine("import heckerpowered.render.memory.MemoryFrame")
    appendLine("import heckerpowered.render.memory.MemoryLayout")
    appendLine("import heckerpowered.render.memory.MemoryStack")
    appendLine("import heckerpowered.render.memory.NativeAddress")
    when (schema.kind) {
        StructKind.Native -> {
            appendLine("import java.nio.ByteBuffer")
            appendLine("import java.nio.FloatBuffer")
            appendLine("import java.nio.IntBuffer")
        }

        StructKind.Uniform -> {
            appendLine("import heckerpowered.render.command.CommandEncoder")
            appendLine("import heckerpowered.render.resource.buffer.BufferUsage")
            appendLine("import heckerpowered.render.resource.buffer.GpuBufferView")
        }

        StructKind.PushConstant -> {
            appendLine("import heckerpowered.render.command.pass.RenderPass")
            appendLine("import heckerpowered.render.pipeline.PushConstantLayout")
            appendLine("import heckerpowered.render.pipeline.PushConstantRange")
            appendLine("import heckerpowered.render.shader.ShaderStage")
        }
    }
    appendLine()
    renderLayout(schema, layout)
    renderView(schema, layout)
    renderScopes(schema, layout)
    if (schema.kind == StructKind.Native) renderAddressScopes(schema, layout)
    if (schema.kind == StructKind.Uniform) renderUpload(schema)
    if (schema.kind == StructKind.PushConstant) renderPush(schema)
}

private fun StringBuilder.renderLayout(schema: StructSchema, layout: StructLayout) {
    if (schema.visibility == "internal") appendLine("@PublishedApi")
    appendLine("${schema.visibility} object ${schema.layoutName} {")
    for ((field, offset) in layout.fields) appendLine("    const val ${field.constantName} = $offset")
    appendLine("    const val SIZE = ${layout.size}")
    appendLine("    const val ALIGNMENT = ${layout.alignment}")
    if (schema.kind == StructKind.PushConstant) {
        appendLine("    const val DESTINATION_OFFSET_BYTES = ${schema.destinationOffset}")
        appendLine("    const val DESTINATION_END_OFFSET_BYTES = ${schema.destinationOffset + layout.size}")
    }
    appendLine("    val memory: MemoryLayout = MemoryLayout.of(SIZE, ALIGNMENT)")
    appendLine("}")
    appendLine()
    when (schema.kind) {
        StructKind.Native -> Unit
        StructKind.Uniform -> {
            appendLine("/** Host byte footprint only; descriptor bindings and GPU storage are supplied separately. */")
            appendLine("${schema.visibility} val ${schema.name}Layout: MemoryLayout = ${schema.layoutName}.memory")
            appendLine()
        }

        StructKind.PushConstant -> {
            appendLine("${schema.visibility} val ${schema.name}Range = PushConstantRange(")
            appendLine("    stages = setOf(${schema.stages.joinToString { "ShaderStage.${quoted(it)}" }}),")
            appendLine("    offsetBytes = ${schema.layoutName}.DESTINATION_OFFSET_BYTES,")
            appendLine("    sizeBytes = ${schema.layoutName}.SIZE,")
            appendLine(")")
            appendLine("${schema.visibility} val ${schema.name}Layout = PushConstantLayout(listOf(${schema.name}Range))")
            appendLine()
        }
    }
}

private fun StringBuilder.renderView(schema: StructSchema, layout: StructLayout) {
    appendLine("/** Borrows live frame memory. This value and every derived view must stay within its allocation scope. */")
    appendLine("@JvmInline")
    appendLine("${schema.visibility} value class ${schema.viewName} @PublishedApi internal constructor(val address: NativeAddress) {")
    for ((field, _) in layout.fields) {
        val offset = "address + ${schema.layoutName}.${field.constantName}"
        when {
            field.nested != null -> {
                val nestedType = qualifiedViewName(field.nested)
                appendLine("    context(_: MemoryFrame)")
                appendLine("    val ${quoted(field.name)}: $nestedType")
                appendLine("        get() = $nestedType($offset)")
            }

            field.isScalar -> {
                appendLine("    context(memoryFrame: MemoryFrame)")
                appendLine("    var ${quoted(field.name)}: ${field.element.name}")
                if (schema.kind == StructKind.Native) {
                    appendLine("        get() = memoryFrame.load${field.element.name}($offset)")
                } else {
                    appendLine("        get() = error(\"Shader data writer properties are write-only\")")
                }
                appendLine("        set(value) = memoryFrame.store${field.element.name}($offset, value)")
            }

            schema.kind == StructKind.Native -> {
                val bufferType = when (field.element) {
                    ElementKind.Float -> "FloatBuffer"
                    ElementKind.Int -> "IntBuffer"
                    ElementKind.Byte -> "ByteBuffer"
                    ElementKind.Struct -> error("Nested record was not emitted as a child view")
                }
                val cast = if (field.element == ElementKind.Byte) "" else ".as${bufferType}()"
                appendLine("    context(memoryFrame: MemoryFrame)")
                appendLine("    val ${quoted(field.name)}: $bufferType")
                appendLine("        get() = memoryFrame.asByteBuffer($offset, ${field.byteSize})$cast")
            }

            field.count in 2..4 -> {
                val components = listOf("x", "y", "z", "w").take(field.count)
                appendLine("    context(memoryFrame: MemoryFrame)")
                appendLine("    fun ${field.setterName}(${components.joinToString { "$it: ${field.element.name}" }}) {")
                if (field.element == ElementKind.Float) {
                    appendLine("        memoryFrame.storeFloat${field.count}($offset, ${components.joinToString()})")
                } else {
                    components.forEachIndexed { index, component ->
                        appendLine("        memoryFrame.storeInt($offset + ${index * 4}, $component)")
                    }
                }
                appendLine("    }")
            }

            else -> {
                appendLine("    /** Writes sixteen column-major components; no transpose or shader-side conversion is performed. */")
                appendLine("    context(memoryFrame: MemoryFrame)")
                appendLine("    fun ${field.setterName}(values: FloatArray) {")
                appendLine("        require(values.size == 16) { \"${field.name} requires sixteen column-major floats\" }")
                appendLine("        memoryFrame.storeFloats($offset, values)")
                appendLine("    }")
            }
        }
        appendLine()
    }
    if (schema.kind == StructKind.Native) {
        appendLine("    context(memoryFrame: MemoryFrame)")
        appendLine("    fun asByteBuffer(): ByteBuffer = memoryFrame.asByteBuffer(address, ${schema.layoutName}.SIZE)")
        val element = schema.nativeElement
        if (element != null) {
            appendLine()
            appendLine("    /** A bounded native query view of this complete record, including declared capacity tails. */")
            appendLine("    context(memoryFrame: MemoryFrame)")
            appendLine("    fun as${element.name}Buffer(): ${element.name}Buffer =")
            appendLine("        memoryFrame.asByteBuffer(address, ${schema.layoutName}.SIZE).as${element.name}Buffer()")
        }
    }
    appendLine("}")
    appendLine()
}

private fun StringBuilder.renderScopes(schema: StructSchema, layout: StructLayout) {
    appendLine("/**")
    appendLine(" * Reserves the complete ${schema.name} record once and exposes its named fields in [block].")
    if (schema.fields.any { it.nested != null }) {
        appendLine(" * Nested views share that reservation; accessing a field does not allocate more native memory.")
    }
    appendLine(" * The frame is restored on every exit. Do not retain the receiver, addresses, or NIO views.")
    appendLine(" */")
    appendLine("${schema.visibility} inline fun <R> MemoryStack.with${schema.name}(")
    appendLine("    block: context(MemoryFrame) ${schema.viewName}.() -> R,")
    appendLine("): R = frame { with${schema.name}(block) }")
    appendLine()
    if (schema.kind != StructKind.Native) {
        appendLine("/** Reserves data in this frame and zeros padding only. Initialize every shader field before consumption. */")
    } else {
        appendLine("/** Reserves uninitialized storage in this frame; [block] does not establish a nested frame. */")
    }
    appendLine("${schema.visibility} inline fun <R> MemoryFrame.with${schema.name}(")
    appendLine("    block: context(MemoryFrame) ${schema.viewName}.() -> R,")
    appendLine("): R {")
    appendLine("    val base = reserve(${schema.layoutName}.SIZE, ${schema.layoutName}.ALIGNMENT)")
    if (schema.kind != StructKind.Native) for (padding in layout.padding) {
        appendLine("    clear(base + ${padding.offset}, ${padding.size})")
    }
    appendLine("    return context(this) { ${schema.viewName}(base).block() }")
    appendLine("}")
    appendLine()
}

private fun StringBuilder.renderAddressScopes(schema: StructSchema, layout: StructLayout) {
    appendLine("${schema.visibility} inline fun <R> MemoryStack.alloc${schema.name}(")
    renderAddressBlock(layout)
    appendLine("): R = frame { alloc${schema.name}(block) }")
    appendLine()
    appendLine("${schema.visibility} inline fun <R> MemoryFrame.alloc${schema.name}(")
    renderAddressBlock(layout)
    appendLine("): R {")
    appendLine("    val base = reserve(${schema.layoutName}.SIZE, ${schema.layoutName}.ALIGNMENT)")
    appendLine("    return block(")
    for (placed in layout.fields) appendLine("        base + ${schema.layoutName}.${placed.field.constantName},")
    appendLine("    )")
    appendLine("}")
    appendLine()
}

private fun StringBuilder.renderAddressBlock(layout: StructLayout) {
    appendLine("    block: MemoryFrame.(")
    for (placed in layout.fields) appendLine("        ${quoted(placed.field.name)}: NativeAddress,")
    appendLine("    ) -> R,")
}

private fun StringBuilder.renderUpload(schema: StructSchema) {
    appendLine("/**")
    appendLine(" * Fills one std140 block and uploads it to an existing buffer range.")
    appendLine(" * The destination must match the complete generated footprint. No GPU storage or binding is allocated.")
    appendLine(" * Initialize every field; only layout padding is zeroed. An exceptional writer does not call writeBuffer.")
    appendLine(" */")
    appendLine("${schema.visibility} inline fun CommandEncoder.write${schema.name}(")
    appendLine("    destination: GpuBufferView,")
    appendLine("    write: context(MemoryFrame) ${schema.viewName}.() -> Unit,")
    appendLine(") {")
    appendLine("    require(destination.sizeBytes == ${schema.layoutName}.SIZE.toLong()) { \"Destination must contain exactly one ${schema.name} block\" }")
    appendLine("    require(BufferUsage.TransferDestination in destination.buffer.usage) { \"Destination requires TransferDestination usage\" }")
    appendLine("    memoryStack.with${schema.name} {")
    appendLine("        write()")
    appendLine("        this@write${schema.name}.writeBuffer(destination, address)")
    appendLine("    }")
    appendLine("}")
}

private fun StringBuilder.renderPush(schema: StructSchema) {
    appendLine("/** Fills and pushes one block. Install ${schema.name}Range in the pipeline layout separately. */")
    appendLine("${schema.visibility} inline fun RenderPass.push${schema.name}(")
    appendLine("    write: context(MemoryFrame) ${schema.viewName}.() -> Unit,")
    appendLine(") {")
    appendLine("    memoryStack.with${schema.name} {")
    appendLine("        write()")
    appendLine("        this@push${schema.name}.pushConstants(")
    appendLine("            ${schema.name}Range.stages, address, ${schema.layoutName}.SIZE,")
    appendLine("            ${schema.layoutName}.DESTINATION_OFFSET_BYTES,")
    appendLine("        )")
    appendLine("    }")
    appendLine("}")
}

private fun qualifiedViewName(schema: StructSchema): String =
    (schema.packageName.split('.').filter { it.isNotEmpty() } + schema.viewName).joinToString(".") { quoted(it) }

private fun quoted(name: String): String = if (name in KotlinKeywords) "`$name`" else name

private val KotlinKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
    "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while",
)
