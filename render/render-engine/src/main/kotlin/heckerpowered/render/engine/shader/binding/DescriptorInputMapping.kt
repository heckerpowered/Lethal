/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.engine.support.collection.toUnmodifiableList
import heckerpowered.render.shader.binding.DescriptorBinding
import heckerpowered.render.shader.binding.DescriptorBindingLayout
import heckerpowered.render.shader.binding.DescriptorSet
import heckerpowered.render.shader.binding.DescriptorSetLayout

/**
 * Maps named shader parameters to the resource slots of one descriptor set.
 *
 * Every binding and array element must be mapped exactly once. Construction validates
 * the mapping against [layout] and arranges parameters in descriptor binding order.
 *
 * Resolution validates all supplied values before preparing resources, so invalid
 * parameters cannot cause partial uniform-buffer allocation.
 */
class DescriptorInputMapping(
    val layout: DescriptorSetLayout,
    parameters: List<DescriptorParameter>,
) {
    val parameters = parameters.toUnmodifiableList()

    private val bindings = buildBindings(this.parameters)

    internal fun resolve(values: ParameterValues, preparation: PassPreparation): DescriptorSet {
        bindings.forEach { it.validate(values, preparation) }

        return DescriptorSet(layout, bindings.map { it.resolve(values, preparation) })
    }

    private fun buildBindings(parameters: List<DescriptorParameter>): List<BindingMapping> {
        val bySlot = parameters.associateBy { it.binding to it.element }

        require(bySlot.size == parameters.size) { "Duplicate descriptor parameter mapping" }

        val bindings = layout.bindings.map { declaration ->
            val mapped = List(declaration.descriptorCount) { element ->
                val parameter = requireNotNull(bySlot[declaration.binding to element]) { "Missing descriptor mapping for binding ${declaration.binding}[$element]" }

                parameter.validateDeclaration(declaration.type)
                parameter
            }

            BindingMapping(declaration, mapped)
        }

        require(bindings.sumOf { it.parameters.size } == parameters.size) { "Descriptor mapping contains undeclared slots" }

        return bindings
    }

    private class BindingMapping(
        val declaration: DescriptorBindingLayout,
        val parameters: List<DescriptorParameter>,
    ) {
        fun validate(values: ParameterValues, preparation: PassPreparation) {
            for (parameter in parameters) {
                parameter.validateValue(declaration.type, values.require(parameter.name), preparation)
            }
        }

        fun resolve(values: ParameterValues, preparation: PassPreparation): DescriptorBinding {
            val resources = parameters.map { parameter ->
                parameter.resolveValidatedResource(declaration.type, values.require(parameter.name), preparation)
            }

            return DescriptorBinding(declaration.binding, resources)
        }
    }
}