/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

/**
 * Device capability that may select a more specialized shader program.
 */
enum class GraphicsFeature {
    NativeUniformBuffers,
}

/**
 * One shader-program representation.
 *
 * Variants are tested in declaration order, so specialized variants should precede the fallback variant.
 */
data class ShaderProgramVariant(
    val vertexShader: ShaderModuleDescription,
    val fragmentShader: ShaderModuleDescription,
    val requiredFeatures: Set<GraphicsFeature> = emptySet(),
) {
    constructor(vertexShader: ShaderSource, fragmentShader: ShaderSource, vararg requiredFeatures: GraphicsFeature) : this(
        ShaderModuleDescription(ShaderStage.Vertex, vertexShader),
        ShaderModuleDescription(ShaderStage.Fragment, fragmentShader),
        requiredFeatures.toSet(),
    )

    init {
        require(vertexShader.stage == ShaderStage.Vertex) { "Shader program requires a vertex shader in the vertex slot" }
        require(fragmentShader.stage == ShaderStage.Fragment) { "Shader program requires a fragment shader in the fragment slot" }
    }
}

/**
 * Ordered representations of the same shader program contract.
 */
data class ShaderProgram(val variants: List<ShaderProgramVariant>) {
    constructor(vararg variants: ShaderProgramVariant) : this(variants.toList())

    init {
        require(variants.isNotEmpty()) { "Shader program requires at least one variant" }
        require(variants.distinctBy(ShaderProgramVariant::requiredFeatures).size == variants.size) { "Shader program variants must have distinct feature requirements" }
        require(variants.last().requiredFeatures.isEmpty()) { "Shader program fallback variant must be declared last" }
    }

    fun select(capabilities: GraphicsCapabilities): ShaderProgramVariant {
        return variants.firstOrNull { variant -> variant.requiredFeatures.all(capabilities::supports) } ?: error("Graphics device does not support any shader program variant")
    }
}

fun GraphicsCapabilities.supports(feature: GraphicsFeature): Boolean {
    return when (feature) {
        GraphicsFeature.NativeUniformBuffers -> supportsNativeUniformBuffers
    }
}
