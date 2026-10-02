/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.math.AffineTransforms
import heckerpowered.math.Matrices
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.command.pass.Viewport
import heckerpowered.render.engine.draw.NonIndexedDrawArguments
import heckerpowered.render.engine.draw.PreparedDrawCommand
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderElement
import heckerpowered.render.engine.scene.drawing.WorldDrawing
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.shader.ShaderStages
import java.lang.reflect.Proxy
import kotlin.test.*

class RasterPassProcessorTest {
    @Test
    fun differentElementDomainsConvergeInSubmissionOrderWithZeroOrManyCommands() {
        val elements = RenderElementPassProcessors()
        elements.install(LabelElement::class.java) { element, _, pass, _ ->
            element.labels.map { command(it, pass) }
        }
        elements.install(MarkerElement::class.java) { element, _, pass, _ -> listOf(command(element.label, pass)) }
        val collection = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {
            submit(LabelElement(listOf("first", "second")))
            submit(MarkerElement("third"))
            submit(LabelElement(emptyList()))
            submit(MarkerElement("fourth"))
        }
        val pass = RasterPass(
            RenderPassDescription("mixed", RenderArea(0, 0, 16, 16)), collection,
            ViewParameters(Matrices.Identity, 16, 16, CompareFunction.Always)
        )
        ResourceLifetime.build {
            try {
                val prepared = RasterPassProcessor(residentDevice(), elements).prepare(pass, this)
                assertEquals(listOf("first", "second", "third", "fourth"), prepared.draws.map { it.pipeline.label })
                assertTrue(prepared.uploads.isEmpty())
                assertTrue(prepared.resources.vertexBuffers.isEmpty())
                assertSame(pass.description, prepared.description)
            } finally {
                close()
            }
        }
    }

    @Test
    fun anUnsupportedElementFailsBeforeGraphicsAccess() {
        val collection = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) { submit(MarkerElement("unknown")) }
        val pass = RasterPass(
            RenderPassDescription("unknown", RenderArea(0, 0, 1, 1)), collection,
            ViewParameters(Matrices.Identity, 1, 1, CompareFunction.Always)
        )
        ResourceLifetime.build {
            try {
                assertFailsWith<IllegalArgumentException> {
                    RasterPassProcessor(residentDevice(), RenderElementPassProcessors()).prepare(pass, this)
                }
            } finally {
                close()
            }
        }
    }

    private fun command(label: String, pass: RasterPass) = PreparedDrawCommand(
        RenderPipelineDescription(label, proxy<ShaderStages>(), colorTargets = emptyList()),
        arguments = NonIndexedDrawArguments(3), viewport = Viewport.from(pass.description.renderArea),
        scissor = ScissorRectangle.from(pass.description.renderArea)
    )

    private fun residentDevice(): GraphicsDevice = proxy()

    private data class LabelElement(val labels: List<String>) : RenderElement
    private data class MarkerElement(val label: String) : RenderElement

    private inline fun <reified T> proxy(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            error("Preparation must not access ${T::class.java.simpleName}.${method.name}")
        } as T
}
