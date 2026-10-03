/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.support.collection.toUnmodifiableList
import heckerpowered.render.engine.support.collection.toUnmodifiableMap
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.reflection.ShaderInterfaceDescription

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class ShaderDefinitionDsl

/** Original vertex and fragment interfaces used to assign application input meanings. */
@ShaderDefinitionDsl
class RasterShaderInterface internal constructor(private val stages: Map<ShaderStage, ShaderInterfaceDescription>) {
    val vertex: ShaderInterfaceDescription = stages.getValue(ShaderStage.Vertex)
    val fragment: ShaderInterfaceDescription = stages.getValue(ShaderStage.Fragment)
    internal val stageFacts: Map<ShaderStage, ShaderInterfaceDescription> get() = stages
}

/**
 * Shares shader code and its fixed input/output contract across typed draw encoders.
 *
 * Canonical code retains its first successful source generation. Replacing this definition selects
 * different code; it does not hot-reload. Output meanings and replay safety are producer promises.
 * Engines own their device programs independently, even when they use the same definition.
 */
class ShaderDefinition internal constructor(
    val label: String,
    val sourceRepresentation: AlphaRepresentation,
    outputs: Map<Int, FragmentOutput>,
    val replaySafe: Boolean,
    private val resolveDefinition: (GraphicsDevice) -> ResolvedMeshShader,
) {
    val outputs = outputs.toUnmodifiableMap()
    internal fun resolve(device: GraphicsDevice): ResolvedMeshShader = resolveDefinition(device)
}

fun shaderDefinition(label: String, block: ShaderDefinitionBuilder.() -> Unit): ShaderDefinition =
    ShaderDefinitionBuilder(label).apply(block).build()

@ShaderDefinitionDsl
class ShaderDefinitionBuilder internal constructor(private val label: String) {
    private var sources: (() -> List<CanonicalShaderModule>)? = null
    private var capturedNative: ResolvedMeshShader? = null
    private var mapInputs: (RasterShaderInterface.() -> ShaderInputLayout)? = null
    private val outputs = linkedMapOf<Int, FragmentOutput>()
    private var declaresOutputs = false
    private var sourceRepresentation: AlphaRepresentation = AlphaRepresentation.Straight
    private var replaySafe: Boolean = false

    /** Reads one coherent source generation, including includes, on first preparation; failed reads remain retryable. */
    fun canonical(sources: () -> List<CanonicalShaderModule>) {
        check(this.sources == null && capturedNative == null) { "Shader code must be declared once" }
        this.sources = sources
    }

    /** Captures already selected target code and its input layout immediately. */
    fun native(modules: List<ShaderModuleDescription>, inputs: ShaderInputLayout) {
        check(sources == null && capturedNative == null) { "Shader code must be declared once" }
        check(mapInputs == null) { "Native code already supplies its input layout" }
        capturedNative = ResolvedMeshShader(modules, inputs)
    }

    fun inputs(mapping: RasterShaderInterface.() -> ShaderInputLayout) {
        check(capturedNative == null) { "Native code already supplies its input layout" }
        check(mapInputs == null) { "Shader inputs must be declared once" }
        mapInputs = mapping
    }

    fun output(location: Int, meaning: FragmentOutput) {
        require(location >= 0) { "Fragment locations must be nonnegative" }
        declaresOutputs = true
        outputs[location] = meaning
    }

    fun sourceRepresentation(value: AlphaRepresentation) {
        sourceRepresentation = value
    }

    fun replaySafe() {
        replaySafe = true
    }

    fun noColorOutputs() {
        declaresOutputs = true
        outputs.clear()
    }

    internal fun build(): ShaderDefinition {
        val outputMeanings = if (declaresOutputs) outputs else mapOf(0 to FragmentOutput(sourceRepresentation))
        val native = capturedNative
        if (native != null) return ShaderDefinition(label, sourceRepresentation, outputMeanings, replaySafe) { native }
        val sources = checkNotNull(sources) { "Shader code is required" }
        val mapInputs = checkNotNull(mapInputs) { "Canonical shader input mapping is required" }
        return ShaderDefinition(label, sourceRepresentation, outputMeanings, replaySafe, canonicalDefinition(sources, mapInputs))
    }
}

private fun canonicalDefinition(sources: () -> List<CanonicalShaderModule>, mapInputs: RasterShaderInterface.() -> ShaderInputLayout): (GraphicsDevice) -> ResolvedMeshShader {
    val snapshot by lazy {
        val modules = sources().toUnmodifiableList()
        val stages = modules.map { it.description.stage }.toSet()
        require(stages == setOf(ShaderStage.Vertex, ShaderStage.Fragment) && modules.size == 2) { "Canonical mesh shaders require one vertex and one fragment stage" }
        modules
    }
    return { device ->
        val compilations = snapshot.map { source ->
            val compilation = device.compileCanonicalShader(source.description, source.origin, source.includes)
            require(compilation.module.stage == source.description.stage && compilation.module.entryPoint == source.description.entryPoint) { "Canonical compilation changed the stage or entry point" }
            compilation
        }
        val reflection = RasterShaderInterface(compilations.associate { it.module.stage to it.reflection }.toUnmodifiableMap())
        val inputLayout = mapInputs(reflection)
        ResolvedMeshShader(compilations.map { it.module }, inputLayout)
    }
}
