/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader.binding

import heckerpowered.render.shader.ShaderStage

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class DescriptorSetLayoutDsl

fun descriptorSetLayout(label: String = "", block: DescriptorSetLayoutBuilder.() -> Unit): DescriptorSetLayout =
    DescriptorSetLayoutBuilder(label).apply(block).build()

@DescriptorSetLayoutDsl
class DescriptorSetLayoutBuilder internal constructor(
    private val label: String,
) {
    private val bindings = mutableListOf<DescriptorBindingLayout>()

    fun binding(binding: Int, type: DescriptorType, vararg stages: ShaderStage, descriptorCount: Int = 1) {
        bindings += DescriptorBindingLayout(binding, type, stages.toSet(), descriptorCount)
    }

    internal fun build(): DescriptorSetLayout =
        DescriptorSetLayout(bindings.toList(), label)
}