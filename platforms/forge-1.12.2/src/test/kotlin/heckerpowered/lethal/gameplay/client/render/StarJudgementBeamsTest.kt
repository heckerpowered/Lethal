/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.lethal.gameplay.client.render.starjudgement.StarJudgementBeamDraw
import heckerpowered.lethal.gameplay.client.render.starjudgement.StarJudgementBeamRenderer
import heckerpowered.lethal.platform.render.worldViewProjectionMatrix
import heckerpowered.lethal.platform.render.ClientWorldRenderPipeline
import net.minecraftforge.fml.common.gameevent.TickEvent
import heckerpowered.math.*
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.TextureParameterValue
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.command.pass.Viewport
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.texture.GpuTextureView
import heckerpowered.render.resource.texture.TextureFormat
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class StarJudgementBeamsTest {
    private val renderer = StarJudgementBeamRenderer(TextureParameterValue(
        proxy<GpuTextureView> { method -> when (method) { "getFormat" -> TextureFormat.Rgba8UnsignedNormalized; else -> error(method) } },
        proxy<GpuSampler> { method -> error(method) },
    ))

    @AfterTest
    fun clearFrame() = StarJudgementBeams.clear()

    @Test
    fun actualHostCollectorDeduplicatesEntityPassesAndRetainsCoreThenShellWithSceneDepth() {
        StarJudgementBeams.submit(7, StarJudgementBeamDraw(Vector(4.0, 5.0, 6.0), 1.25))
        val origin = Vector(30_000_000.125, 74.25, -30_000_000.5)
        StarJudgementBeams.submit(7, StarJudgementBeamDraw(origin, 1.5))
        val list = StarJudgementBeams.collect(renderer)
        assertEquals(2, list.submissions.size)
        for ((index, submission) in list.submissions.withIndex()) {
            assertEquals(origin, submission.objectState.localToWorld.translation)
            assertEquals(DepthMode.Scene, submission.visibility)
            assertEquals(index == 0, (submission.element as GeometryElement).depthWrite)
        }
        assertTrue(StarJudgementBeams.collect(renderer).submissions.isEmpty())
    }

    @Test
    fun hostCollectorWorldPlacementCancelsCameraExactlyOnceBeforeFinalFloatShaderPayload() {
        val camera = Vector(30_000_000.75, 74.0, -30_000_000.25)
        val origin = camera + Vector(0.125, -0.25, -3.0)
        StarJudgementBeams.submit(7, StarJudgementBeamDraw(origin, 1.5))
        val submission = StarJudgementBeams.collect(renderer).submissions.first()
        val identity = floatArrayOf(1F, 0F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, 0F, 1F)
        val view = ViewParameters.fromClipMatrix(worldViewProjectionMatrix(identity, identity, camera), 1280, 720, CompareFunction.LessOrEqual)
        val value = view.forObject(submission.objectState, Viewport(0F, 0F, 1280F, 720F)).requireNumeric(ParameterName("clipFromLocal"))
        val bytes = ByteBuffer.allocate(64).order(ByteOrder.nativeOrder())
        value.copyTo(bytes)
        val floats = (bytes.flip() as ByteBuffer).asFloatBuffer()
        assertEquals(0.125F, floats.get(12))
        assertEquals(0.25F, floats.get(13))
        assertEquals(-1F, floats.get(14))
    }

    @Test
    fun emptyEntityPhaseDoesNotEnterGraphicsRendering() {
        assertFalse(StarJudgementBeams.hasPending())
        ClientWorldRenderPipeline.afterEntityRender(0.5F)
        assertFalse(StarJudgementBeams.hasPending())
    }

    @Test
    fun clearingAnAbortedFrameCannotReplayAnEntityInTheNextFrame() {
        StarJudgementBeams.submit(7, StarJudgementBeamDraw(Vectors.Zero, 1.5))
        ClientWorldRenderPipeline.onRenderTick(TickEvent.RenderTickEvent(TickEvent.Phase.START, 0.5F))
        assertTrue(StarJudgementBeams.collect(renderer).submissions.isEmpty())
    }

    private inline fun <reified T> proxy(crossinline invoke: (String) -> Any?): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> invoke(method.name) } as T
}
