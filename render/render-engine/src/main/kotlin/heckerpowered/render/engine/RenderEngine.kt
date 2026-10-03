/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.image.RenderImageStore
import heckerpowered.render.engine.pass.GeometryElementPassProcessor
import heckerpowered.render.engine.pass.RasterPassProcessor
import heckerpowered.render.engine.pass.RenderElementPassProcessors
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.shader.program.MeshShader
import heckerpowered.render.engine.shader.program.ShaderRealizations
import heckerpowered.render.engine.stage.RenderStageBuilder
import heckerpowered.render.engine.stage.RenderStageExecutor
import heckerpowered.render.engine.stage.RenderStageProcessor
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.lifetime
import heckerpowered.render.resource.sampler.SamplerAddressMode
import heckerpowered.render.resource.sampler.SamplerMipmapMode
import heckerpowered.render.resource.sampler.TextureFilter
import heckerpowered.render.resource.sampler.createSampler
import heckerpowered.render.terminateOnFailure

/**
 * Prepares submitted geometry and effects as ordered rendering stages on one graphics device.
 *
 * Use [stage] to declare passes and transfers together. The engine resolves geometry and bound shaders
 * into concrete draws before recording, so each RHI pass receives its resource declaration at entry.
 * The image store and device shader implementations persist across stages; submitted scene data is not retained
 * as an engine-managed scene.
 *
 * Successful stage execution waits for GPU completion. If that wait fails, temporary allocations
 * remain registered until [awaitIdle] succeeds. [close] performs final release without waiting;
 * call [awaitIdle] first whenever pending GPU work may still use engine resources.
 *
 * The engine does not release the graphics device or resources supplied by callers. Its default
 * image sampler is created and released with the engine. Keep caller-supplied resources valid
 * through their GPU uses. Calls must be serialized and satisfy the device's
 * thread and graphics-context requirements.
 */
class RenderEngine internal constructor(
    val images: RenderImageStore,
    private val raster: RasterPassProcessor,
    private val executor: RenderStageExecutor,
    private val lifetime: ResourceLifetime,
    private val shaders: ShaderRealizations,
) : AutoCloseable {
    /**
     * Establishes this shader's device modules, linked stages and layout before its first draw.
     *
     * First-use preparation reuses the same engine cache. Call during loading on the device's
     * permitted thread/context to move these operations out of gameplay. This does not create
     * every pipeline combination or guarantee that the driver performs no later compilation.
     */
    fun prepare(shader: MeshShader<*>) {
        lifetime.checkOpen()
        executor.checkReady()
        shaders.require(shader)
    }

    /**
     * Collects and prepares a stage, then records its operations in declaration order.
     *
     * The callback completes before preparation begins. Building copies the operation list; later
     * additions to the builder do not alter the stage prepared for this execution.
     *
     * Recording and the following GPU wait may fail. A failed wait retains temporary resources and
     * blocks another execution until [awaitIdle] succeeds. The callback does not provide rollback for
     * resource creation or for commands already recorded before a failure.
     */
    fun stage(block: RenderStageBuilder.() -> Unit) {
        lifetime.checkOpen()
        executor.checkReady()
        val builder = RenderStageBuilder()
        builder.block()
        executor.execute(RenderStageProcessor.prepare(builder.build(), raster))
        images.releaseRetired()
    }

    /**
     * Establishes GPU completion and releases temporary or replaced image allocations awaiting it.
     *
     * A failed wait leaves those allocations registered for a later retry. This is an explicit fallible
     * operation, separate from final resource release in [close].
     */
    fun awaitIdle() {
        lifetime.checkOpen()
        executor.awaitIdle()
        images.releaseRetired()
    }

    override fun close() = terminateOnFailure { lifetime.close() }

    companion object {
        /**
         * Creates raster-pass processing and an image store for [device].
         *
         * Submitted shaders are realized on first use. Effects and builtin CPU definitions are
         * supplied by the application rather than selected during engine construction.
         * The engine uses [GraphicsDevice.awaitIdle] before releasing temporary or retired
         * resources. Callers must separately establish completion of image uses outside the
         * device's execution stream before those allocations can be released.
         *
         * Store images share an engine-created linear clamp-to-edge sampler with mip sampling
         * disabled. It is released with the other engine-created resources, including when
         * construction fails. The engine does not release the supplied device.
         */
        fun create(device: GraphicsDevice): RenderEngine = ResourceLifetime.build {
            val filterSampler = device.createSampler(
                minificationFilter = TextureFilter.Linear,
                magnificationFilter = TextureFilter.Linear,
                addressModeU = SamplerAddressMode.ClampToEdge,
                addressModeV = SamplerAddressMode.ClampToEdge,
                addressModeW = SamplerAddressMode.ClampToEdge,
                mipmapMode = SamplerMipmapMode.Disabled,
            ).lifetime(this)
            val shaders = ShaderRealizations(device, this)
            val processors = RenderElementPassProcessors()
            processors.install(GeometryElement::class.java, GeometryElementPassProcessor(shaders))

            val executor = RenderStageExecutor(device).lifetime(this)
            val images = RenderImageStore(device, this, filterSampler)
            val rasterProcessor = RasterPassProcessor(device, processors, images)
            RenderEngine(images, rasterProcessor, executor, this, shaders)
        }
    }
}
