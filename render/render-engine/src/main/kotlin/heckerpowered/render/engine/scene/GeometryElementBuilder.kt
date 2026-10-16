/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.shader.program.MeshShading

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class GeometryElementDsl

/** Configures a bound element's color composition and depth writes. Omitted depth writes follow the pass. */
@GeometryElementDsl
class GeometryElementBuilder internal constructor(private val shading: MeshShading) {
    private val compositions = linkedMapOf<Int, CompositingMode>()
    private var depthWrite: Boolean? = null

    fun composition(mode: CompositingMode, location: Int = 0) {
        compositions[location] = mode
    }

    fun depthWrite(enabled: Boolean) {
        depthWrite = enabled
    }

    internal fun build(): GeometryElement = GeometryElement(shading, compositions, depthWrite = depthWrite)
}
