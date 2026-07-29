/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

/**
 * Immutable, backend-independent description used by a graphics device to create a render pipeline.
 *
 * A pipeline description owns no GPU resource. Devices may cache the pipeline produced from this value until the
 * device closes.
 */
data class RenderPipelineDescription(
    val label: String,
    val shaderProgram: ShaderProgram,
    val vertexBufferLayout: VertexBufferLayout = VertexBufferLayout.Empty,
    val primitiveTopology: PrimitiveTopology = PrimitiveTopology.TriangleList,
    val blendState: BlendState = BlendState.Disabled,
    val depthState: DepthState = DepthState.Disabled,
    val cullMode: CullMode = CullMode.None,
    val descriptorSetLayouts: List<DescriptorSetLayout> = emptyList(),
    val pushConstants: PushConstantLayout? = null,
) {
    init {
        require(label.isNotBlank()) { "Render pipeline label must not be blank" }
    }
}

class RenderPipelineBuilder internal constructor(private val label: String) {
    private var shaderProgram: ShaderProgram? = null
    private var vertexBufferLayout: VertexBufferLayout? = null
    private var primitiveTopology: PrimitiveTopology? = null
    private var blendState: BlendState? = null
    private var depthState: DepthState? = null
    private var cullMode: CullMode? = null
    private val descriptorSetLayouts = mutableListOf<DescriptorSetLayout>()
    private var pushConstantLayout: PushConstantLayout? = null

    fun shaders(program: ShaderProgram) {
        check(shaderProgram == null) { "Render pipeline shaders have already been declared" }
        shaderProgram = program
    }

    fun vertexBuffer(layout: VertexBufferLayout) {
        check(vertexBufferLayout == null) { "Render pipeline vertex buffer has already been declared" }
        vertexBufferLayout = layout
    }

    fun descriptorSet(set: Int, layout: DescriptorSetLayout) {
        require(set == descriptorSetLayouts.size) { "Descriptor set ${descriptorSetLayouts.size} must be declared next, but received set $set" }
        descriptorSetLayouts += layout
    }

    fun pushConstants(layout: PushConstantLayout) {
        check(pushConstantLayout == null) { "Render pipeline push constants have already been declared" }
        pushConstantLayout = layout
    }

    fun topology(topology: PrimitiveTopology) {
        check(primitiveTopology == null) { "Render pipeline topology has already been declared" }
        primitiveTopology = topology
    }

    fun blend(blend: BlendState) {
        check(blendState == null) { "Render pipeline blending has already been declared" }
        blendState = blend
    }

    fun depth(depth: DepthState) {
        check(depthState == null) { "Render pipeline depth state has already been declared" }
        depthState = depth
    }

    fun cull(cull: CullMode) {
        check(cullMode == null) { "Render pipeline cull mode has already been declared" }
        cullMode = cull
    }

    internal fun build(): RenderPipelineDescription {
        return RenderPipelineDescription(
            label,
            requireNotNull(shaderProgram) { "Render pipeline shaders have not been declared" },
            vertexBufferLayout ?: VertexBufferLayout.Empty,
            primitiveTopology ?: PrimitiveTopology.TriangleList,
            blendState ?: BlendState.Disabled,
            depthState ?: DepthState.Disabled,
            cullMode ?: CullMode.None,
            descriptorSetLayouts.toList(),
            pushConstantLayout,
        )
    }
}

fun renderPipeline(label: String, declaration: RenderPipelineBuilder.() -> Unit): RenderPipelineDescription {
    return RenderPipelineBuilder(label).apply(declaration).build()
}
