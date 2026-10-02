/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.AffineTransforms
import heckerpowered.math.Geometry
import heckerpowered.math.Matrices
import heckerpowered.math.MatrixView
import heckerpowered.render.command.pass.Viewport
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.shader.parameter.ParameterDerivations
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class ViewParametersTest {
    @Test
    fun clipMatrixFactoryPreservesDoublePrecisionUntilObjectComposition() {
        val cameraX = 1_073_741_824.25
        val shared = ParameterValues()
        val view = ViewParameters.fromClipMatrix(Matrices.fromTranslation(Geometry.vector(-cameraX, 0.0, 0.0)), 64, 32, CompareFunction.Always, shared)
        val context = ObjectSubmitContext(AffineTransforms.fromTranslation(Geometry.vector(cameraX + 0.125, 0.0, 0.0)))
        val parameters = view.forObject(context, Viewport(0f, 0f, 64f, 32f))
        val clip = ByteBuffer.wrap(parameters.requireNumeric(ParameterName("clipFromLocal")).bytes()).order(ByteOrder.nativeOrder()).asFloatBuffer()
        assertEquals(0.125f, clip[12])
        assertEquals(-cameraX, view.worldToClip.m03)
        assertEquals(64, view.width)
        assertEquals(32, view.height)
        assertEquals(CompareFunction.Always, view.depthCompare)
        assertSame(shared, view.parameters)
    }

    @Test
    fun clipMatrixFactorySnapshotsMutableSourceBeforeComposition() {
        var translation = 0.25
        val source = object : MatrixView {
            override val m00 = 1.0
            override val m01 = 0.0
            override val m02 = 0.0
            override val m03 get() = translation
            override val m10 = 0.0
            override val m11 = 1.0
            override val m12 = 0.0
            override val m13 = 0.0
            override val m20 = 0.0
            override val m21 = 0.0
            override val m22 = 1.0
            override val m23 = 0.0
            override val m30 = 0.0
            override val m31 = 0.0
            override val m32 = 0.0
            override val m33 = 1.0
        }
        val view = ViewParameters.fromClipMatrix(source, 1, 1, CompareFunction.Always)
        translation = 10.0
        assertNotSame(source, view.worldToClip)
        assertEquals(0.25, view.worldToClip.m03)
        val parameters = view.forObject(ObjectSubmitContext(AffineTransforms.Identity), Viewport(0f, 0f, 1f, 1f))
        val clip = ByteBuffer.wrap(parameters.requireNumeric(ParameterName("clipFromLocal")).bytes()).order(ByteOrder.nativeOrder()).asFloatBuffer()
        assertEquals(0.25f, clip[12])
    }

    @Test
    fun largeWorldPlacementCancelsBeforeConversionToFloat() {
        val cameraX = 1_073_741_824.25
        val view = ViewParameters(Matrices.fromTranslation(Geometry.vector(-cameraX, 0.0, 0.0)), 64, 32, CompareFunction.Always)
        val context = ObjectSubmitContext(AffineTransforms.fromTranslation(Geometry.vector(cameraX + 0.125, 0.0, 0.0)))
        val parameters = view.forObject(context, Viewport(0f, 0f, 64f, 32f))
        val clip = ByteBuffer.wrap(parameters.requireNumeric(ParameterName("clipFromLocal")).bytes()).order(ByteOrder.nativeOrder()).asFloatBuffer()
        assertEquals(0.125f, clip[12])
        assertEquals(-cameraX, view.worldToClip[0, 3])
    }

    @Test
    fun suppliedViewRemainsASnapshotWhenItsSourceChanges() {
        var translation = 0.25
        val source = object : MatrixView by Matrices.Identity {
            override val m03 get() = translation
        }
        val view = ViewParameters(source, 1, 1, CompareFunction.Always)
        translation = 10.0
        assertEquals(0.25, view.worldToClip[0, 3])
    }

    @Test
    fun normalDerivationUsesTheOriginalDoubleTransform() {
        // Rounding this block to Float first would make its two first axes identical and singular.
        val model = Matrices.of(
            1.0, 1.0, 0.0, 0.0,
            1.0, 1.0 + 1e-8, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0,
        )
        val parameters = ParameterDerivations(true).resolve(ParameterValues(), model)
        val normal = ByteBuffer.wrap(parameters.requireNumeric(ParameterName("normalFromLocal")).bytes()).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val transformedX = normal[0].toDouble() + normal[1].toDouble()
        assertTrue(normal[0].isFinite())
        assertEquals(0.0, transformedX, 1.0)
        assertEquals(1f, normal[10])
    }
}
