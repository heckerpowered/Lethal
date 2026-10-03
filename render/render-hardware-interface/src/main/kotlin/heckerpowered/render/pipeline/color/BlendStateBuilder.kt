/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.color

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class BlendStateDsl

/** Declares RGB and alpha equations independently; both must be supplied exactly once. */
fun blendState(block: BlendStateBuilder.() -> Unit): BlendState =
    BlendStateBuilder().apply(block).build()

@BlendStateDsl
class BlendStateBuilder internal constructor() {
    private var color: BlendComponent? = null
    private var alpha: BlendComponent? = null

    fun color(sourceFactor: BlendFactor, destinationFactor: BlendFactor, operation: BlendOperation = BlendOperation.Add) {
        check(color == null) { "Blend state may declare only one color equation" }
        color = BlendComponent(sourceFactor, destinationFactor, operation)
    }

    fun alpha(sourceFactor: BlendFactor, destinationFactor: BlendFactor, operation: BlendOperation = BlendOperation.Add) {
        check(alpha == null) { "Blend state may declare only one alpha equation" }
        alpha = BlendComponent(sourceFactor, destinationFactor, operation)
    }

    internal fun build(): BlendState = BlendState(
        checkNotNull(color) { "Blend state must declare a color equation" },
        checkNotNull(alpha) { "Blend state must declare an alpha equation" },
    )
}
