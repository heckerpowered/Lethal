/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.culling

import heckerpowered.math.*
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.pass.GeometryElementPassProcessor
import heckerpowered.render.engine.pass.RasterPass
import heckerpowered.render.engine.pass.visible
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.scene.RenderSubmissionList
import heckerpowered.render.engine.shader.binding.VertexInterface
import heckerpowered.render.engine.shader.program.GeometryInput
import heckerpowered.render.engine.shader.program.MeshShader
import heckerpowered.render.engine.shader.program.ShaderRealizations
import heckerpowered.render.engine.view.Frustum
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.shader.ShaderLanguage
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderSource
import heckerpowered.render.shader.ShaderStage
import java.lang.reflect.Proxy
import kotlin.test.*

class CullingBoundsTest {
    private val frustum = Frustum.fromWorldToClip(Matrices.Identity)
    private val inside = Geometry.box(-.25, -.25, .2, .25, .25, .8)

    @Test
    fun boxesRetainIntersectionsBoundariesAndPointsAndRejectSeparatedPlacements() {
        assertFalse(BoxCullingBounds(inside).canCull(frustum, AffineTransforms.Identity))
        assertTrue(BoxCullingBounds(inside).canCull(frustum, AffineTransforms.fromTranslation(Vectors.of(3.0, 0.0, 0.0))))
        val outside = BoxCullingBounds(Geometry.box(2.0, -.25, .2, 3.0, .25, .8))
        assertTrue(outside.canCull(frustum, AffineTransforms.Identity))
        assertFalse(outside.canCull(frustum, AffineTransforms.fromTranslation(Vectors.of(-2.0, 0.0, 0.0))))
        assertFalse(BoxCullingBounds(Geometry.box(1.0, 0.0, .1, 1.2, .1, .2)).canCull(frustum, AffineTransforms.Identity))
        assertFalse(BoxCullingBounds(Geometry.box(1.0, 0.0, .5, 1.0, 0.0, .5)).canCull(frustum, AffineTransforms.Identity))
        assertTrue(BoxCullingBounds(Geometry.box(1.01, 0.0, .5, 1.01, 0.0, .5)).canCull(frustum, AffineTransforms.Identity))
    }

    @Test
    fun rotationsNonuniformScaleReflectionAndShearAgreeWithTransformedCorners() {
        val placements = listOf(
            AffineTransforms.of(axisX = Vectors.UnitY, axisY = Vectors.NegativeUnitX),
            AffineTransforms.of(axisX = Vectors.of(-2.0, 0.0, 0.0), axisY = Vectors.of(0.0, .5, 0.0)),
            AffineTransforms.of(axisY = Vectors.of(1.5, 1.0, 0.0)),
            AffineTransforms.of(axisX = Vectors.of(0.0, -2.0, 0.0), axisY = Vectors.of(1.0, .5, 0.0)),
        )
        val bounds = BoxCullingBounds(inside)
        for (placement in placements) {
            val separated = AffineTransforms.of(placement.axisX, placement.axisY, placement.axisZ, Vectors.of(4.0, 0.0, 0.0))
            for (transform in listOf(placement, separated)) {
                val corners = inside.vertices().map { transform.transformPosition(it) }
                val worldBox = Geometry.box(
                    corners.minOf { it.x },
                    corners.minOf { it.y },
                    corners.minOf { it.z },
                    corners.maxOf { it.x },
                    corners.maxOf { it.y },
                    corners.maxOf { it.z }
                )
                assertEquals(!frustum.intersects(worldBox), bounds.canCull(frustum, transform))
            }
            assertFalse(bounds.canCull(frustum, placement))
            assertTrue(bounds.canCull(frustum, separated))
        }
    }

    @Test
    fun constructionSnapshotsBoxAndQueriesSamplePlacementBeforeValidation() {
        var offset = 2.0
        val reads = IntArray(6)
        val box = object : BoxView {
            override val minX: Double
                get() {
                    reads[0]++; return offset
                }
            override val minY: Double
                get() {
                    reads[1]++; return -.25
                }
            override val minZ: Double
                get() {
                    reads[2]++; return .2
                }
            override val maxX: Double
                get() {
                    reads[3]++; return offset + 1.0
                }
            override val maxY: Double
                get() {
                    reads[4]++; return .25
                }
            override val maxZ: Double
                get() {
                    reads[5]++; return .8
                }
        }
        val bounds = BoxCullingBounds(box)
        offset = -.5
        repeat(2) { assertTrue(bounds.canCull(frustum, AffineTransforms.Identity)) }
        assertContentEquals(IntArray(6) { 1 }, reads)
        val translationReads = IntArray(3)
        val translation = object : VectorView {
            override val x: Double get() = if (++translationReads[0] == 1) -2.0 else Double.NaN
            override val y: Double get() = if (++translationReads[1] == 1) 0.0 else Double.NaN
            override val z: Double get() = if (++translationReads[2] == 1) 0.0 else Double.NaN
        }
        val placement = object : AffineTransformView by AffineTransforms.Identity {
            override val translation: VectorView = translation
        }
        assertFalse(bounds.canCull(frustum, placement))
        assertContentEquals(IntArray(3) { 1 }, translationReads)
    }

    @Test
    fun invalidNonfiniteAndOverflowingQueriesCannotProveExclusion() {
        for (box in listOf(
            Geometry.box(Double.NaN, 0.0, .2, 3.0, .1, .8),
            Geometry.box(2.0, 0.0, .2, Double.POSITIVE_INFINITY, .1, .8),
            Geometry.box(Double.NEGATIVE_INFINITY, 0.0, .2, 3.0, .1, .8),
            Geometry.box(3.0, 0.0, .2, 2.0, .1, .8),
            Geometry.box(Double.MAX_VALUE, 0.0, .2, Double.MAX_VALUE, .1, .8),
        )) assertFalse(BoxCullingBounds(box).canCull(frustum, AffineTransforms.Identity))
        val bounds = BoxCullingBounds(Geometry.box(2.0, 0.0, .2, 3.0, .1, .8))
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(bounds.canCull(frustum, AffineTransforms.fromTranslation(Vectors.of(value, 0.0, 0.0))))
            assertFalse(bounds.canCull(frustum, AffineTransforms.of(axisX = Vectors.of(value, 0.0, 0.0))))
        }
        assertFalse(bounds.canCull(frustum, AffineTransforms.of(axisX = Vectors.of(Double.MAX_VALUE, 0.0, 0.0))))
    }

    @Test
    fun largeWorldTranslationKeepsDoublePrecisionAndZeroScaleNeedsNoInverse() {
        val origin = 1.0E12 + .125
        val distant = Frustum.fromWorldToClip(
            Matrices.of(
                1.0, 0.0, 0.0, -origin,
                0.0, 1.0, 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0
            )
        )
        val placement = AffineTransforms.fromTranslation(Vectors.of(origin, 0.0, 0.0))
        assertFalse(BoxCullingBounds(Geometry.box(.5, 0.0, .5, .5, 0.0, .5)).canCull(distant, placement))
        assertTrue(BoxCullingBounds(Geometry.box(1.5, 0.0, .5, 1.5, 0.0, .5)).canCull(distant, placement))
        val bounds = BoxCullingBounds(inside)
        assertFalse(bounds.canCull(frustum, AffineTransforms.of(Vectors.Zero, Vectors.Zero, Vectors.Zero, Vectors.of(0.0, 0.0, .5))))
        assertTrue(bounds.canCull(frustum, AffineTransforms.of(Vectors.Zero, Vectors.Zero, Vectors.Zero, Vectors.of(3.0, 0.0, .5))))
    }

    @Test
    fun visibilityConsumesOnlyTheCapabilityAndRetainsMissingOrUnknownBounds() {
        val inputs = ViewParameters(Matrices.Identity, 1, 1, CompareFunction.Always)
        val geometry = ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState())
        val shader = MeshShader<Unit>(
            listOf(
                ShaderModuleDescription(
                    ShaderStage.Vertex,
                    ShaderSource(ShaderLanguage.Glsl, "CPU fixture", "test")
                ),
                ShaderModuleDescription(
                    ShaderStage.Fragment,
                    ShaderSource(ShaderLanguage.Glsl, "CPU fixture", "test")
                )
            ),
            VertexInterface(emptyList()), label = "culling fixture", encode = { GeometryInput(geometry) })
        val context = ObjectSubmitContext(AffineTransforms.fromTranslation(Vectors.of(3.0, 0.0, 0.0)))
        val element = shader.bind(Unit)
        val submission = RenderSubmission(element, context)
        assertTrue(visible(submission, inputs))
        var calls = 0
        val rejecting = CullingBounds { queriedFrustum, placement ->
            calls++
            assertSame(inputs.frustum, queriedFrustum)
            assertEquals(3.0, placement.translation.x)
            true
        }
        assertFalse(visible(submission.copy(element = element.copy(cullingBounds = rejecting)), inputs))
        assertEquals(1, calls)
        val rejectedElement = element.copy(cullingBounds = rejecting)
        val rejectedSubmission = submission.copy(element = rejectedElement)
        ResourceLifetime.build {
            try {
                val device = Proxy.newProxyInstance(GraphicsDevice::class.java.classLoader, arrayOf(GraphicsDevice::class.java)) { _, method, _ ->
                    error("Culled geometry must not access the device: ${method.name}")
                } as GraphicsDevice
                val processor = GeometryElementPassProcessor(ShaderRealizations(device, this))
                val pass = RasterPass(
                    RenderPassDescription("culling", RenderArea(0, 0, 1, 1)),
                    RenderSubmissionList(listOf(rejectedSubmission)),
                    inputs
                )
                assertTrue(processor.prepare(rejectedElement, rejectedSubmission, pass, PassPreparation(device, this)).isEmpty())
            } finally {
                close()
            }
        }
        assertEquals(2, calls)
        assertTrue(visible(submission.copy(element = element.copy(cullingBounds = CullingBounds { _, _ -> false })), inputs))
    }
}
