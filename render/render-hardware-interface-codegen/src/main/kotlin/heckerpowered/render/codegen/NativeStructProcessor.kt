/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.codegen

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate

/**
 * Reads annotated declarations and emits one independent source file per schema.
 * Only names of completed outputs survive a processing round: symbols and resolved types do not.
 */
internal class NativeStructProcessor(
    private val generator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private val emitted = HashSet<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = ArrayList<KSAnnotated>()
        val visited = HashSet<String>()
        for (annotationName in SchemaAnnotations.keys) {
            for (symbol in resolver.getSymbolsWithAnnotation(annotationName)) {
                val declaration = symbol as? KSClassDeclaration
                if (declaration == null) {
                    logger.error("A memory schema must annotate an interface", symbol)
                    continue
                }
                val name = declaration.qualifiedName?.asString()
                if (name != null && (!visited.add(name) || name in emitted)) continue
                if (!declaration.validate(COMPILED_CODE, enableNewFeatures = false)) {
                    deferred += declaration
                    continue
                }
                val source = declaration.containingFile
                if (name == null || source == null) {
                    logger.error("A memory schema must be a top-level source declaration", declaration)
                    continue
                }
                val dependencies = linkedSetOf(source)
                val schema = try {
                    readSchema(declaration, resolver, emptySet(), dependencies).also {
                        validateGeneratedNames(it, resolver)
                    }
                } catch (_: UnresolvedSchema) {
                    deferred += declaration
                    continue
                } catch (failure: IllegalArgumentException) {
                    logger.error(failure.message ?: "Invalid memory schema", declaration)
                    continue
                }
                val layout = try {
                    StructLayout.calculate(schema)
                } catch (failure: IllegalArgumentException) {
                    logger.error(failure.message ?: "Invalid memory layout", declaration)
                    continue
                }
                val output = renderStruct(schema, layout)
                generator.createNewFile(
                    Dependencies(aggregating = false, *dependencies.toTypedArray()),
                    schema.packageName,
                    "${schema.name}NativeStruct",
                ).bufferedWriter(Charsets.UTF_8).use { it.write(output) }
                emitted += name
            }
        }
        return deferred
    }
}

// This root is retried by KSP when a nested schema still depends on a later processing round.
private class UnresolvedSchema : RuntimeException()

private val SchemaAnnotations = mapOf(
    "heckerpowered.render.memory.NativeStruct" to StructKind.Native,
    // These declarations still use the root package in the restored production baseline.
    "heckerpowered.render.GpuBufferData" to StructKind.Uniform,
    "heckerpowered.render.PushConstantBlock" to StructKind.PushConstant,
)
private const val NativeAddressName = "heckerpowered.render.memory.NativeAddress"
private const val FloatElementsName = "heckerpowered.render.memory.FloatElements"
private const val IntElementsName = "heckerpowered.render.memory.IntElements"
private const val BytesName = "heckerpowered.render.memory.Bytes"
private val FieldAnnotations = setOf(FloatElementsName, IntElementsName, BytesName)

@OptIn(KspExperimental::class)
private fun readSchema(
    declaration: KSClassDeclaration,
    resolver: Resolver,
    ancestors: Set<String>,
    dependencies: MutableSet<KSFile>,
): StructSchema {
    val qualifiedName = requireNotNull(declaration.qualifiedName).asString()
    require(qualifiedName !in ancestors) { "Cyclic native structure: ${(ancestors.toList() + qualifiedName).joinToString(" -> ")}" }
    if (!declaration.validate(COMPILED_CODE, enableNewFeatures = false)) throw UnresolvedSchema()
    dependencies += requireNotNull(declaration.containingFile) { "Nested NativeStruct schemas must be declared in this compilation's sources" }
    validateDeclaration(declaration)
    val annotations = declaration.annotations.filter { it.qualifiedName() in SchemaAnnotations }.toList()
    require(annotations.size == 1) { "Use exactly one of NativeStruct, GpuBufferData, or PushConstantBlock" }
    val annotation = annotations.single()
    val kind = SchemaAnnotations.getValue(annotation.qualifiedName())
    // Source order is part of the byte ABI. KSP's ordinary declarations sequence is not an ABI ordering guarantee.
    val members = resolver.getDeclarationsInSourceOrder(declaration).toList()
    require(members.all { it is KSPropertyDeclaration }) { "Schemas contain only abstract properties, not functions or nested declarations" }
    val fields = members.filterIsInstance<KSPropertyDeclaration>().map { property ->
        readField(property, kind) { nested ->
            readSchema(nested, resolver, ancestors + qualifiedName, dependencies)
        }
    }
    val schema = StructSchema(
        packageName = declaration.packageName.asString(),
        name = declaration.simpleName.asString(),
        visibility = if (Modifier.INTERNAL in declaration.modifiers) "internal" else "public",
        kind = kind,
        fields = fields,
        minimumAlignment = if (kind == StructKind.Native) annotation.intArgument("alignment", 1) else 1,
        destinationOffset = if (kind == StructKind.PushConstant) annotation.intArgument("offsetBytes", 0) else 0,
        stages = if (kind == StructKind.PushConstant) annotation.stageNames() else emptyList(),
    )
    return schema
}

private fun validateDeclaration(declaration: KSClassDeclaration) {
    val isTopLevelInterface = declaration.classKind == ClassKind.INTERFACE && declaration.parentDeclaration == null
    val isVisibleFromGeneratedFile = Modifier.PRIVATE !in declaration.modifiers && Modifier.PROTECTED !in declaration.modifiers
    val isPlatformDeclaration = Modifier.EXPECT in declaration.modifiers || Modifier.ACTUAL in declaration.modifiers
    val hasNoInheritedFields = declaration.superTypes.all { expanded(it.resolve()).declaration.qualifiedName?.asString() == "kotlin.Any" }
    require(declaration.origin == Origin.KOTLIN) { "Only Kotlin source schemas are supported" }
    require(isTopLevelInterface) { "A memory schema must be a top-level interface" }
    require(declaration.typeParameters.isEmpty()) { "Generic schemas are not supported" }
    require(isVisibleFromGeneratedFile) { "Use internal or public visibility; generated declarations live in a separate file" }
    require(!isPlatformDeclaration) { "Expected/actual schemas are not supported by this JVM processor" }
    require(hasNoInheritedFields) { "Inherited fields need an explicit ordering rule; declare every field directly in this schema" }
}

private fun validateGeneratedNames(schema: StructSchema, resolver: Resolver) {
    for (generatedName in listOf(schema.layoutName, schema.viewName)) {
        val fullName = listOf(schema.packageName, generatedName).filter { it.isNotEmpty() }.joinToString(".")
        val existingDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(fullName))
        require(existingDeclaration == null) { "Generated name already exists: $fullName" }
    }
}

private fun readField(
    property: KSPropertyDeclaration,
    kind: StructKind,
    readNested: (KSClassDeclaration) -> StructSchema,
): StructField {
    val name = property.simpleName.asString()
    val isInstanceValue = !property.isMutable && property.extensionReceiver == null
    val hasNoImplementation = !property.hasBackingField && !property.isDelegated()
    require(isInstanceValue && hasNoImplementation) { "$name must be an immutable abstract instance property" }
    require(property.isAbstract()) { "$name must not have a custom getter" }
    val resolvedType = property.type.resolve()
    require(!resolvedType.isMarkedNullable) { "$name must not be nullable" }
    val type = expanded(resolvedType)
    require(!type.isMarkedNullable && type.arguments.isEmpty()) { "$name must have a non-null, non-generic field type" }
    val typeName = type.declaration.qualifiedName?.asString()
    val layouts = property.annotations.filter { it.qualifiedName() in FieldAnnotations }.toList()
    if (typeName == NativeAddressName) {
        require(layouts.size == 1) { "$name needs exactly one of FloatElements, IntElements, or Bytes" }
        val layout = layouts.single()
        val count = layout.intArgument("count")
        return when (layout.qualifiedName()) {
            FloatElementsName -> StructField(name, ElementKind.Float, count)
            IntElementsName -> StructField(name, ElementKind.Int, count)
            else -> {
                require(kind == StructKind.Native) { "$name: raw Bytes has no shader-field representation" }
                StructField(name, ElementKind.Byte, count, layout.intArgument("alignment", 1))
            }
        }
    }
    require(layouts.isEmpty()) { "$name: element annotations apply to NativeAddress fields only" }
    return when (typeName) {
        "kotlin.Float" -> StructField(name, ElementKind.Float)
        "kotlin.Int" -> StructField(name, ElementKind.Int)
        "heckerpowered.render.Float2" -> {
            require(kind != StructKind.Native) { "$name: shader vector markers are not host scalar fields" }
            StructField(name, ElementKind.Float, 2)
        }

        "heckerpowered.render.Float3" -> {
            require(kind != StructKind.Native) { "$name: shader vector markers are not host scalar fields" }
            StructField(name, ElementKind.Float, 3)
        }

        "heckerpowered.render.Float4" -> {
            require(kind != StructKind.Native) { "$name: shader vector markers are not host scalar fields" }
            StructField(name, ElementKind.Float, 4)
        }

        else -> {
            val nested = type.declaration as? KSClassDeclaration
            val isNativeStructure = nested?.annotations?.any { it.qualifiedName() == "heckerpowered.render.memory.NativeStruct" } == true
            require(kind == StructKind.Native && isNativeStructure) { "$name: unsupported field type $typeName" }
            StructField(name, ElementKind.Struct, nested = readNested(requireNotNull(nested)))
        }
    }
}

private fun expanded(type: KSType): KSType {
    val declaration = type.declaration
    return if (declaration is KSTypeAlias) expanded(declaration.type.resolve()) else type
}

private fun KSAnnotation.qualifiedName(): String = annotationType.resolve().declaration.qualifiedName?.asString().orEmpty()
private fun KSAnnotation.intArgument(name: String, default: Int? = null): Int =
    (arguments.firstOrNull { it.name?.asString() == name }?.value as? Int) ?: default
    ?: throw IllegalArgumentException("Missing integer annotation argument $name")

private fun KSAnnotation.stageNames(): List<String> {
    val values = arguments.firstOrNull { it.name?.asString() == "stages" }?.value as? List<*>
        ?: throw IllegalArgumentException("PushConstantBlock requires shader stages")
    return values.map { value ->
        val declaration = when (value) {
            is KSType -> value.declaration
            is KSClassDeclaration -> value
            else -> throw IllegalArgumentException("Unresolved shader-stage argument")
        }
        val enumTypeName = declaration.parentDeclaration?.qualifiedName?.asString()
        require(enumTypeName == "heckerpowered.render.shader.ShaderStage") { "Expected a ShaderStage enum entry" }
        declaration.simpleName.asString()
    }
}
