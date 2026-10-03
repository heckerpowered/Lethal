/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.shader.program

import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.*

/** Test-double facts for mapping/cache tests; no compiler or driver executes in this fixture. */
internal fun fixtureScreenPreparation(description: ShaderModuleDescription, origin: String, binding: Int = 0, pushOffset: Int = 0): ShaderCompilation {
    fun vector(components: Int) = ShaderValueDescription(ShaderScalarKind.Float, 32, components, 1)
    val coordinates = ShaderInterfaceVariable("coordinates", 0, vector(2))
    val facts = if (description.stage == ShaderStage.Vertex) {
        ShaderInterfaceDescription(emptyList(), listOf(coordinates), emptyList())
    } else {
        val image = ShaderInterfaceResource(
            ShaderInterfaceResourceKind.CombinedTextureSampler,
            "image",
            0,
            binding,
            emptyList(),
            null,
            null,
            emptyList(),
            ShaderImageDescription(ShaderImageDimension.TwoDimensional, false, false, false, vector(1)),
        )
        val field = when (origin) {
            "copy.frag" -> null
            "tent.frag" -> "texelSize" to 2
            "brightness.frag" -> "threshold" to 1
            else -> error("Unexpected screen fixture source: $origin")
        }
        val resources = if (field == null) listOf(image) else {
            val size = field.second.toLong() * Float.SIZE_BYTES
            val member = ShaderInterfaceBlockMember(field.first, pushOffset, size, vector(field.second), 0, 0, false)
            listOf(image, ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "params", null, null, emptyList(), pushOffset + size, "Params", listOf(member), null))
        }
        ShaderInterfaceDescription(listOf(coordinates), listOf(ShaderInterfaceVariable("result", 0, vector(4))), resources)
    }
    return ShaderCompilation(description.copy(code = ShaderSource(ShaderLanguage.Glsl, "native fixture code", description.label)), facts)
}
