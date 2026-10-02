/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.pipeline.PushConstantLayout
import heckerpowered.render.pipeline.PushConstantRange
import heckerpowered.render.pipeline.createPipelineLayout
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.lifetime
import heckerpowered.render.shader.createShaderStages
import java.util.*

/**
 * Reuses one device implementation for each shader definition used by this engine.
 *
 * Definitions are selected by object identity, independently of their labels. Failed construction
 * releases its partial resources without caching a program. Successful resources belong to the
 * engine lifetime, so sharing a CPU definition does not share device ownership between engines.
 */
internal class ShaderRealizations(
    private val device: GraphicsDevice,
    private val lifetime: ResourceLifetime,
) {
    private val programs = IdentityHashMap<MeshShader<*>, ShaderProgram>()

    fun require(shader: MeshShader<*>): ShaderProgram {
        lifetime.checkOpen()
        return programs.getOrPut(shader) {
            val [program, programLifetime] = ResourceLifetime.build {
                val modules = shader.modules.map { device.createShaderModule(it).lifetime(this) }
                val stages = device.createShaderStages(modules, shader.label).lifetime(this)
                val inputs = shader.inputs
                val pushConstants = inputs.pushes.takeIf { it.isNotEmpty() }?.let { blocks ->
                    PushConstantLayout(blocks.map { PushConstantRange(it.stages, it.offsetBytes, it.sizeBytes) })
                }
                val layout = device.createPipelineLayout(inputs.descriptors.map { it.layout }, pushConstants, shader.label).lifetime(this)
                ShaderProgram(stages, layout, inputs, shader.sourceRepresentation, shader.replaySafe) to this
            }
            programLifetime.lifetime(lifetime)
            program
        }
    }
}
