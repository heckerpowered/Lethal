/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.codegen

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Creates the KSP processor for native-memory structure declarations.
 */
class NativeStructProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return NativeStructProcessor(environment.codeGenerator, environment.logger)
    }
}

private class NativeStructProcessor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) : SymbolProcessor {
    private val generatedDeclarations = mutableSetOf<String>()
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val nativeStructures = resolver.getSymbolsWithAnnotation(NATIVE_STRUCT_ANNOTATION).toList()
        val pushConstantBlocks = resolver.getSymbolsWithAnnotation(PUSH_CONSTANT_BLOCK_ANNOTATION).toList()
        val gpuBufferData = resolver.getSymbolsWithAnnotation(GPU_BUFFER_DATA_ANNOTATION).toList()
        val symbols = (nativeStructures + pushConstantBlocks + gpuBufferData).distinct()
        val deferredSymbols = symbols.filterNot(KSAnnotated::validate)
        symbols.asSequence()
            .filter(KSAnnotated::validate)
            .filterIsInstance<KSClassDeclaration>()
            .filter { generatedDeclarations.add(requireNotNull(it.qualifiedName).asString()) }
            .forEach(::generate)
        return deferredSymbols
    }

    private fun generate(declaration: KSClassDeclaration) {
        val definition = try {
            declaration.definition()
        } catch (_: ArithmeticException) {
            logger.error("Native structure ${declaration.simpleName.asString()} exceeds the supported size", declaration)
            return
        } ?: return

        val source = try {
            definition.render()
        } catch (_: ArithmeticException) {
            logger.error("Native structure ${definition.typeName} exceeds the supported size", declaration)
            return
        } catch (exception: IllegalArgumentException) {
            logger.error(exception.message ?: "Native structure layout is invalid", declaration)
            return
        }

        val sourceFile = requireNotNull(declaration.containingFile)
        codeGenerator.createNewFile(Dependencies(aggregating = false, sourceFile), definition.packageName, "${definition.typeName}NativeStruct").use { output ->
            OutputStreamWriter(output, StandardCharsets.UTF_8).use { it.write(source) }
        }
    }

    private fun KSClassDeclaration.definition(): NativeStructDefinition? {
        var isValid = true
        fun reject(message: String, symbol: KSAnnotated = this) {
            logger.error(message, symbol)
            isValid = false
        }

        val isGpuBufferData = hasAnnotation(GPU_BUFFER_DATA_ANNOTATION)
        val nativeStructAnnotation = annotations.singleOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == NATIVE_STRUCT_ANNOTATION }
        if (isGpuBufferData && nativeStructAnnotation != null) reject("@GpuBufferData already defines its native layout and must not be combined with @NativeStruct")

        val pushConstantAnnotation = annotations.singleOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == PUSH_CONSTANT_BLOCK_ANNOTATION }
        if (isGpuBufferData && pushConstantAnnotation != null) reject("@GpuBufferData must not be combined with @PushConstantBlock")
        if (nativeStructAnnotation != null && pushConstantAnnotation != null) reject("@PushConstantBlock already defines its native layout and must not be combined with @NativeStruct")

        if (classKind != ClassKind.INTERFACE) reject("Native layouts may only be declared by interfaces")
        if (parentDeclaration != null) reject("Native layout interfaces must be top-level declarations")
        if (typeParameters.isNotEmpty()) reject("Native layout interfaces cannot declare type parameters")
        if (superTypes.map { it.resolve().declaration.qualifiedName?.asString() }.any { it != "kotlin.Any" }) reject("Native layout interfaces cannot inherit another type")
        if (Modifier.PRIVATE in modifiers || Modifier.PROTECTED in modifiers) reject("Native layout interfaces must be internal or public")

        val typeName = simpleName.asString()
        if (!typeName.isKotlinIdentifier()) reject("Native layout interface names must be ordinary Kotlin identifiers")

        val properties = declarations.filterIsInstance<KSPropertyDeclaration>().toList()
        if (properties.isEmpty()) reject("Native layout interfaces must declare at least one property")
        val usesShaderLayout = isGpuBufferData || pushConstantAnnotation != null
        val fields = properties.mapNotNull { property -> property.field(usesShaderLayout, pushConstantAnnotation != null, ::reject) }
        val minimumAlignment = when {
            isGpuBufferData -> STD140_STRUCTURE_ALIGNMENT
            nativeStructAnnotation != null -> nativeStructAnnotation.intArgument("alignment")
            else -> 1
        }
        if (minimumAlignment <= 0 || minimumAlignment and (minimumAlignment - 1) != 0) reject("Native layout alignment must be a positive power of two")
        val pushConstantBlock = pushConstantAnnotation?.let { annotation ->
            PushConstantBlockDefinition(annotation.intArgument("offsetBytes"), annotation.shaderStages())
        }
        if (pushConstantBlock != null && (pushConstantBlock.offsetBytes < 0 || pushConstantBlock.offsetBytes % Int.SIZE_BYTES != 0)) {
            reject("@PushConstantBlock offsetBytes must be non-negative and four-byte aligned")
        }
        if (pushConstantBlock?.stages?.isEmpty() == true) reject("@PushConstantBlock requires at least one shader stage")
        if (fields.size != properties.size) isValid = false
        if (!isValid) return null

        val visibility = if (Modifier.INTERNAL in modifiers) "internal" else ""
        return NativeStructDefinition(packageName.asString(), typeName, visibility, fields, minimumAlignment, pushConstantBlock, isGpuBufferData)
    }

    private fun KSPropertyDeclaration.field(usesShaderLayout: Boolean, usesTypedShaderField: Boolean, reject: (String, KSAnnotated) -> Unit): NativeStructField? {
        var isValid = true
        fun rejectProperty(message: String) {
            reject(message, this)
            isValid = false
        }

        val propertyName = simpleName.asString()
        if (!propertyName.isKotlinIdentifier()) rejectProperty("Native structure property names must be ordinary Kotlin identifiers")
        if (isMutable) rejectProperty("Native structure properties must be immutable")
        if (extensionReceiver != null) rejectProperty("Native structure properties cannot have extension receivers")
        if (getter != null && Modifier.ABSTRACT !in requireNotNull(getter).modifiers) rejectProperty("Native structure properties cannot define a getter")
        if (hasBackingField || isDelegated()) rejectProperty("Native structure properties cannot store a value")

        val propertyType = type.resolve()
        val layoutAnnotations = annotations.mapNotNull { annotation -> annotation.layoutOrNull() }.toList()
        if (usesTypedShaderField) {
            if (layoutAnnotations.isNotEmpty()) rejectProperty("Push-constant properties declare their layout through the property type")
            val layout = propertyType.shaderFieldLayoutOrNull()
            if (layout == null) rejectProperty("Push-constant properties must use Int, Float, Float2, Float3, Float4, or Matrix4")
            if (!isValid) return null
            return requireNotNull(layout).nativeField(propertyName)
        }

        if (propertyType.nullability != Nullability.NOT_NULL || propertyType.declaration.qualifiedName?.asString() != NATIVE_ADDRESS_TYPE) {
            rejectProperty("Native structure properties must have the non-null NativeAddress type")
        }

        if (layoutAnnotations.size != 1) {
            rejectProperty("Native structure properties must declare exactly one of @FloatElements, @IntElements, or @Bytes")
            return null
        }

        val layout = layoutAnnotations.single()
        if (layout.byteCount <= 0) rejectProperty("Native structure fields must contain at least one byte")
        val alignment = if (usesShaderLayout) layout.gpuAlignment else layout.alignment
        if (usesShaderLayout && alignment == null) rejectProperty("Shader-visible native fields must be one int, 1-4 floats, or a 4 x 4 float matrix")
        if (alignment != null && (alignment <= 0 || alignment and (alignment - 1) != 0)) rejectProperty("Native structure field alignment must be a positive power of two")
        if (!isValid) return null
        return NativeStructField(propertyName, propertyName.screamingSnakeCase(), layout.elementType, layout.elementCount, requireNotNull(alignment))
    }

    private fun KSType.shaderFieldLayoutOrNull(): FieldLayout? {
        if (nullability != Nullability.NOT_NULL) return null
        return when (declaration.qualifiedName?.asString()) {
            "kotlin.Int" -> FieldLayout(NativeElementType.Int, 1, Int.SIZE_BYTES, GpuValueType.Int.alignment)
            "kotlin.Float" -> FieldLayout(NativeElementType.Float, 1, Float.SIZE_BYTES, GpuValueType.Float.alignment)
            FLOAT2_TYPE -> FieldLayout(NativeElementType.Float, 2, Float.SIZE_BYTES, GpuValueType.Float2.alignment)
            FLOAT3_TYPE -> FieldLayout(NativeElementType.Float, 3, Float.SIZE_BYTES, GpuValueType.Float3.alignment)
            FLOAT4_TYPE -> FieldLayout(NativeElementType.Float, 4, Float.SIZE_BYTES, GpuValueType.Float4.alignment)
            MATRIX4_TYPE -> FieldLayout(NativeElementType.Float, 16, Float.SIZE_BYTES, GpuValueType.Matrix4.alignment)
            else -> null
        }
    }

    private fun KSAnnotation.layoutOrNull(): FieldLayout? {
        val annotationName = annotationType.resolve().declaration.qualifiedName?.asString()
        return when (annotationName) {
            FLOAT_ELEMENTS_ANNOTATION -> elementLayout(NativeElementType.Float)
            INT_ELEMENTS_ANNOTATION -> elementLayout(NativeElementType.Int)
            BYTES_ANNOTATION -> FieldLayout(NativeElementType.Byte, intArgument("count"), intArgument("alignment"), null)
            else -> null
        }
    }

    private fun KSAnnotation.elementLayout(elementType: NativeElementType): FieldLayout {
        val elementCount = intArgument("count")
        val gpuAlignment = when (elementType) {
            NativeElementType.Int -> GpuValueType.Int.alignment.takeIf { elementCount == 1 }
            NativeElementType.Float -> when (elementCount) {
                1 -> GpuValueType.Float.alignment
                2 -> GpuValueType.Float2.alignment
                3 -> GpuValueType.Float3.alignment
                4 -> GpuValueType.Float4.alignment
                16 -> GpuValueType.Matrix4.alignment
                else -> null
            }

            NativeElementType.Byte -> null
        }
        return FieldLayout(elementType, elementCount, elementType.byteCount, gpuAlignment)
    }

    private fun KSAnnotation.intArgument(name: String): Int {
        return arguments.single { it.name?.asString() == name }.value as Int
    }

    private fun KSAnnotation.shaderStages(): List<GpuShaderStage> {
        val values = arguments.single { it.name?.asString() == "stages" }.value as List<*>
        return values.map { value ->
            val stageName = when (value) {
                is KSClassDeclaration -> value.simpleName.asString()
                is KSType -> value.declaration.simpleName.asString()
                else -> error("Unsupported shader stage annotation value: $value")
            }
            GpuShaderStage.entries.single { it.sourceName == stageName }
        }.distinct()
    }
}

private fun KSAnnotated.hasAnnotation(qualifiedName: String): Boolean = annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName }

private data class FieldLayout(val elementType: NativeElementType, val elementCount: Int, val alignment: Int, val gpuAlignment: Int?) {
    val byteCount: Int = Math.multiplyExact(elementCount, elementType.byteCount)

    fun nativeField(name: String): NativeStructField {
        return NativeStructField(name, name.screamingSnakeCase(), elementType, elementCount, requireNotNull(gpuAlignment))
    }
}

private fun String.screamingSnakeCase(): String {
    return replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .uppercase()
}

private fun String.isKotlinIdentifier(): Boolean = isNotEmpty() && first().let { it == '_' || it.isLetter() } && drop(1).all { it == '_' || it.isLetterOrDigit() }

private const val NATIVE_STRUCT_ANNOTATION = "heckerpowered.render.memory.NativeStruct"
private const val PUSH_CONSTANT_BLOCK_ANNOTATION = "heckerpowered.render.PushConstantBlock"
private const val GPU_BUFFER_DATA_ANNOTATION = "heckerpowered.render.GpuBufferData"
private const val FLOAT_ELEMENTS_ANNOTATION = "heckerpowered.render.memory.FloatElements"
private const val INT_ELEMENTS_ANNOTATION = "heckerpowered.render.memory.IntElements"
private const val BYTES_ANNOTATION = "heckerpowered.render.memory.Bytes"
private const val NATIVE_ADDRESS_TYPE = "heckerpowered.render.memory.NativeAddress"
private const val FLOAT2_TYPE = "heckerpowered.render.Float2"
private const val FLOAT3_TYPE = "heckerpowered.render.Float3"
private const val FLOAT4_TYPE = "heckerpowered.render.Float4"
private const val MATRIX4_TYPE = "heckerpowered.render.Matrix4"
private const val STD140_STRUCTURE_ALIGNMENT = 16
