/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

import heckerpowered.render.shader.binding.DescriptorSetLayout

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class PipelineLayoutDsl

fun pipelineLayout(label: String, block: PipelineLayoutDescriptionBuilder.() -> Unit): PipelineLayoutDescription =
    PipelineLayoutDescriptionBuilder(label).apply(block).build()

@PipelineLayoutDsl
class PipelineLayoutDescriptionBuilder internal constructor(
    private val label: String,
) {
    private val descriptorSets = mutableListOf<DescriptorSetLayout>()
    private var pushConstants: PushConstantLayout? = null

    fun descriptorSet(layout: DescriptorSetLayout) {
        descriptorSets += layout
    }

    fun pushConstants(layout: PushConstantLayout) {
        check(pushConstants == null) { "Pipeline layout may declare only one push-constant layout" }
        pushConstants = layout
    }

    internal fun build(): PipelineLayoutDescription =
        PipelineLayoutDescription(descriptorSets.toList(), pushConstants, label)
}