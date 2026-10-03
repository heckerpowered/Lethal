/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.command.pass.RenderPass
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.command.pass.RenderPassResources
import heckerpowered.render.engine.pass.PreparedRenderPass
import heckerpowered.render.resource.ResourceLifetime
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RenderStageExecutorTest {
    @Test
    fun anEmptyDrawListStillEntersItsRenderPass() {
        val events = mutableListOf<String>()
        val description = RenderPassDescription("empty", renderArea = RenderArea(0, 0, 1, 1))
        val pass = proxy<RenderPass> { _, _ -> error("An empty pass must not emit draw commands") }
        val encoder = proxy<CommandEncoder> { name, arguments ->
            check(name == "renderPass")
            assertSame(description, arguments[0])
            assertSame(RenderPassResources.Empty, arguments[1])
            events += "begin pass"
            @Suppress("UNCHECKED_CAST")
            (arguments[2] as RenderPass.() -> Unit).invoke(pass)
            events += "end pass"
            null
        }
        val device = proxy<GraphicsDevice> { name, arguments ->
            when (name) {
                "encode" -> {
                    @Suppress("UNCHECKED_CAST")
                    (arguments[1] as CommandEncoder.() -> Unit).invoke(encoder)
                }

                "awaitIdle" -> events += "idle"
                else -> error(name)
            }
            null
        }
        val prepared = ResourceLifetime.build {
            PreparedRenderStage(listOf(PreparedStageOperation.Pass(PreparedRenderPass(description, emptyList(), emptyList()))), this)
        }
        RenderStageExecutor(device).execute(prepared)
        assertEquals(listOf("begin pass", "end pass", "idle"), events)
    }

    @Test
    fun aReleasedStageCannotRecordMoreCommands() {
        val events = mutableListOf<String>()
        val device = proxy<GraphicsDevice> { name, _ ->
            when (name) {
                "encode" -> events += "encode"
                "awaitIdle" -> events += "idle"
                else -> error(name)
            }
            null
        }
        val prepared = ResourceLifetime.build { PreparedRenderStage(emptyList(), this) }
        val executor = RenderStageExecutor(device)

        executor.execute(prepared)
        assertFailsWith<IllegalStateException> { prepared.temporaryLifetime.checkOpen() }
        assertFailsWith<IllegalStateException> { executor.execute(prepared) }
        executor.close()
        assertEquals(listOf("encode", "idle"), events)
    }

    private inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments ->
            invoke(method.name.substringBefore('-'), arguments ?: emptyArray())
        } as T
}
