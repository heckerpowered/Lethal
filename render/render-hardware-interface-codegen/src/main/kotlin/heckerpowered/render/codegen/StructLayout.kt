/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.codegen

/**
 * The byte representation of a declaration, computed before any source is emitted.
 * Native fields are packed by their host element alignment. Shader fields instead follow the
 * supported scalar, vector, and column-major mat4 subset of std140 or std430.
 */
internal data class StructLayout(
    val fields: List<PlacedField>,
    val size: Int,
    val alignment: Int,
    val padding: List<Padding>,
) {
    companion object {
        fun calculate(schema: StructSchema): StructLayout {
            require(schema.fields.isNotEmpty()) { "A memory declaration needs at least one field" }
            requirePowerOfTwo(schema.minimumAlignment)
            val fieldNamesAreUnique = schema.fields.map { it.name }.distinct().size == schema.fields.size
            val constantNamesAreUnique = schema.fields.map { it.constantName }.distinct().size == schema.fields.size
            require(fieldNamesAreUnique) { "Duplicate field names" }
            require(constantNamesAreUnique) { "Field names produce duplicate offset constants" }
            val alignment = maxOf(
                schema.minimumAlignment,
                if (schema.kind == StructKind.Uniform) 16 else 1,
                schema.fields.maxOf { it.alignment(schema.kind) },
            )
            val padding = ArrayList<Padding>()
            var end = 0L
            val fields = schema.fields.map { field ->
                val start = align(end, field.alignment(schema.kind))
                if (start > end) padding += Padding(end.toInt(), (start - end).toInt())
                end = start + field.byteSize
                require(end <= Int.MAX_VALUE) { "Structure exceeds the Int-sized host reservation limit" }
                PlacedField(field, start.toInt())
            }
            val size = align(end, alignment)
            if (size > end) padding += Padding(end.toInt(), (size - end).toInt())
            if (schema.kind == StructKind.PushConstant) {
                require(schema.stages.isNotEmpty()) { "Push constants need at least one shader stage" }
                require(schema.stages.distinct().size == schema.stages.size) { "Duplicate shader stages" }
                schema.stages.forEach(::requireIdentifier)
                require(schema.destinationOffset >= 0 && schema.destinationOffset % 4 == 0) { "Push constant offset must be non-negative and four-byte aligned" }
                require(size <= Int.MAX_VALUE.toLong() - schema.destinationOffset) { "Push constant range end exceeds Int capacity" }
                // Members retain their relative offsets at this destination. Merely aligning
                // the host allocation cannot fix an unaligned shader-visible destination.
                val membersRemainAligned = fields.all {
                    val destinationAddress = it.offset.toLong() + schema.destinationOffset
                    destinationAddress % it.field.alignment(schema.kind) == 0L
                }
                require(membersRemainAligned) { "Push constant destination misaligns a shader member" }
            }
            return StructLayout(fields, size.toInt(), alignment, padding)
        }

        private fun align(value: Long, alignment: Int): Long {
            val result = (value + alignment - 1L) and -alignment.toLong()
            require(result <= Int.MAX_VALUE) { "Aligned structure size exceeds Int capacity" }
            return result
        }
    }
}

internal enum class StructKind { Native, Uniform, PushConstant }
internal enum class ElementKind { Float, Int, Byte, Struct }

internal data class StructSchema(
    val packageName: String,
    val name: String,
    val visibility: String,
    val kind: StructKind,
    val fields: List<StructField>,
    val minimumAlignment: Int = 1,
    val destinationOffset: Int = 0,
    val stages: List<String> = emptyList(),
) {
    init {
        requireIdentifier(name)
        require(visibility == "internal" || visibility == "public") { "Only public and internal declarations are supported" }
        val packageNameIsValid = packageName.isEmpty() || packageName.split('.').all { Identifier.matches(it) }
        val reservedNames = setOf("address", "asByteBuffer", "asIntBuffer", "asFloatBuffer")
        val fieldNamesAreAvailable = fields.none { it.name in reservedNames }
        val nestedFieldsAreSupported = kind == StructKind.Native || fields.none { it.nested != null }
        val exposesInternalView = visibility == "public" && fields.any { it.nested != null && it.nested.visibility != "public" }
        require(packageNameIsValid) { "Package segments must be ordinary Kotlin identifiers" }
        require(fieldNamesAreAvailable) { "address and NIO conversion names are reserved by the generated view" }
        require(nestedFieldsAreSupported) { "Nested records currently describe host memory, not shader block layout" }
        require(!exposesInternalView) { "A public memory schema cannot expose an internal nested view" }
    }

    val layoutName: String get() = if (kind == StructKind.Native) "${name}Layout" else "${name}MemoryLayout"
    val viewName: String get() = if (kind == StructKind.Native) "${name}View" else "${name}Writer"

    // Typed NIO views are offered only when all leaves have the same numeric representation.
    // This includes explicit query-capacity tails, but never reinterprets mixed fields.
    val nativeElement: ElementKind?
        get() = fields.map { it.nested?.nativeElement ?: it.element }.distinct().singleOrNull()
            ?.takeIf { it == ElementKind.Int || it == ElementKind.Float }

}

internal data class StructField(
    val name: String,
    val element: ElementKind,
    val count: Int = 1,
    val byteAlignment: Int = 1,
    val nested: StructSchema? = null,
) {
    init {
        requireIdentifier(name)
        require((element == ElementKind.Struct) == (nested != null)) { "A nested field needs its structure schema" }
        val isCompleteNativeRecord = count == 1 && byteAlignment == 1 && nested?.kind == StructKind.Native
        require(nested == null || isCompleteNativeRecord) { "A nested record occupies one complete native layout" }
        require(count > 0) { "Field element count must be positive" }
        requirePowerOfTwo(byteAlignment)
        require(count.toLong() * elementSize <= Int.MAX_VALUE) { "Field byte size exceeds Int capacity" }
    }

    val elementSize: Int get() = if (element == ElementKind.Byte) 1 else 4
    val nestedLayout: StructLayout? = nested?.let(StructLayout::calculate)
    val byteSize: Int get() = nestedLayout?.size ?: count * elementSize
    val isScalar: Boolean get() = (element == ElementKind.Float || element == ElementKind.Int) && count == 1
    val constantName: String
        get() = name
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .uppercase(java.util.Locale.ROOT) + "_OFFSET"
    val setterName: String get() = "set" + name.replaceFirstChar { it.uppercaseChar() }

    fun alignment(kind: StructKind): Int {
        if (kind == StructKind.Native) return nestedLayout?.alignment ?: maxOf(elementSize, byteAlignment)
        require(element != ElementKind.Byte && element != ElementKind.Struct) { "Raw byte fields have no supported shader representation" }
        val isScalarOrVector = count in 1..4
        val isFloatMatrix = element == ElementKind.Float && count == 16
        require(isScalarOrVector || isFloatMatrix) { "Shader fields support 1–4 scalar/vector components, or 16 floats for a column-major mat4; arrays need a separate schema" }
        return when (count) {
            1 -> 4
            2 -> 8
            else -> 16
        }
    }
}

internal data class PlacedField(val field: StructField, val offset: Int)
internal data class Padding(val offset: Int, val size: Int)

private val Identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
internal fun requireIdentifier(name: String) {
    require(Identifier.matches(name)) { "Generated declarations require ordinary identifier characters: $name" }
}

private fun requirePowerOfTwo(value: Int) {
    require(value > 0 && value and (value - 1) == 0) { "Alignment must be a positive power of two" }
}
