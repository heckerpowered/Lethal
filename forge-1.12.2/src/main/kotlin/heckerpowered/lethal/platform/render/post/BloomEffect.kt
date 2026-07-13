/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render.post

import heckerpowered.lethal.platform.render.*
import net.minecraft.client.renderer.OpenGlHelper
import org.apache.logging.log4j.LogManager

internal object BloomEffect {
    private const val SCREEN_QUAD_VERTEX_SHADER = "/assets/lethal/shaders/core/screen_quad.vsh"
    private const val BRIGHTNESS_FRAGMENT_SHADER = "/assets/lethal/shaders/post/bloom/bloom_brightness_pass.fsh"
    private const val TENT_FRAGMENT_SHADER = "/assets/lethal/shaders/post/blur/tent.fsh"
    private val Logger = LogManager.getLogger("Lethal Bloom")

    private var brightnessProgram: BlitProgram? = null
    private var tentProgram: BlitProgram? = null
    private var brightFramebuffer: ManagedColorRenderTarget? = null
    private var contentFramebuffer: ManagedColorRenderTarget? = null
    private val bloomDownFramebuffers = mutableListOf<ManagedColorRenderTarget>()
    private val bloomUpFramebuffers = mutableListOf<ManagedColorRenderTarget>()
    private var framebufferDimensions: FramebufferDimensions? = null
    private var initializationFailed = false

    val isSupported: Boolean
        get() = !initializationFailed && OpenGlHelper.openGL21 && OpenGlHelper.areShadersSupported() && OpenGlHelper.isFramebufferEnabled()

    fun prepare(width: Int, height: Int): Boolean {
        if (!isSupported) return false

        return try {
            RenderStateIsolation.isolate {
                ensurePrograms()
                PostProcessRenderer.prepare()
                ensureFramebuffers(width, height)
            }
            true
        } catch (throwable: Throwable) {
            disablePipeline("Failed to initialize the Bloom pipeline", throwable)
            false
        }
    }

    fun apply(source: ColorRenderTarget, target: RenderSurface, brightnessThreshold: Float): Boolean {
        if (!prepare(source.width, source.height)) return false

        return try {
            RenderStateIsolation.isolate {
                val bloomFramebuffer = renderBloom(source, brightnessThreshold)
                PostProcessRenderer.copy(bloomFramebuffer, target, Blending.AdditiveColor)
            }
            true
        } catch (throwable: Throwable) {
            disablePipeline("Failed to render Bloom", throwable)
            false
        }
    }

    fun renderContent(target: RenderSurface, brightnessThreshold: Float, renderContent: () -> Unit): Boolean {
        if (!prepare(target.width, target.height)) return false

        val source = requireNotNull(contentFramebuffer)
        if (!clearContentFramebuffer(source)) return false

        val previousSurface = try {
            BoundRenderSurface.capture()
        } catch (throwable: Throwable) {
            disablePipeline("Failed to capture the render target before Bloom content rendering", throwable)
            return false
        }

        try {
            PostProcessRenderer.bind(RenderSurface(source.framebufferObject, source.width, source.height))
        } catch (throwable: Throwable) {
            restoreSurface(previousSurface)?.let(throwable::addSuppressed)
            disablePipeline("Failed to bind the Bloom content framebuffer", throwable)
            return false
        }

        var contentFailure: Throwable? = null
        try {
            renderContent()
        } catch (throwable: Throwable) {
            contentFailure = throwable
        }

        val restorationFailure = restoreSurface(previousSurface)
        if (contentFailure != null) {
            restorationFailure?.let(contentFailure::addSuppressed)
            throw contentFailure
        }

        return completeContentRendering(source, target, brightnessThreshold, restorationFailure)
    }

    private fun clearContentFramebuffer(source: ManagedColorRenderTarget): Boolean {
        return try {
            RenderStateIsolation.isolate { PostProcessRenderer.clear(source) }
            true
        } catch (throwable: Throwable) {
            disablePipeline("Failed to clear the Bloom content framebuffer", throwable)
            false
        }
    }

    private fun completeContentRendering(source: ManagedColorRenderTarget, target: RenderSurface, brightnessThreshold: Float, precedingFailure: Throwable?): Boolean {
        if (precedingFailure != null) return recoverContent(source, target, precedingFailure)

        var contentCopied = false
        try {
            RenderStateIsolation.isolate {
                val bloomFramebuffer = renderBloom(source, brightnessThreshold)
                PostProcessRenderer.copy(source, target, Blending.PremultipliedAlpha)
                contentCopied = true
                PostProcessRenderer.copy(bloomFramebuffer, target, Blending.Additive)
            }
        } catch (throwable: Throwable) {
            return recoverContent(source, target, throwable, contentCopied)
        }
        return true
    }

    private fun recoverContent(source: ManagedColorRenderTarget, target: RenderSurface, pipelineFailure: Throwable, contentCopied: Boolean = false): Boolean {
        if (!contentCopied) {
            try {
                RenderStateIsolation.isolate { PostProcessRenderer.copy(source, target, Blending.PremultipliedAlpha) }
            } catch (fallbackFailure: Throwable) {
                pipelineFailure.addSuppressed(fallbackFailure)
                disablePipeline("Failed to render Bloom content and its fallback", pipelineFailure)
                throw pipelineFailure
            }
        }

        disablePipeline("Failed to render Bloom content; the original content was preserved", pipelineFailure)
        return true
    }

    private fun restoreSurface(surface: BoundRenderSurface): Throwable? {
        return try {
            surface.restore()
            null
        } catch (throwable: Throwable) {
            throwable
        }
    }

    private fun clearBloomPasses() {
        PostProcessRenderer.clear(requireNotNull(brightFramebuffer))
        bloomDownFramebuffers.forEach(PostProcessRenderer::clear)
        bloomUpFramebuffers.forEach(PostProcessRenderer::clear)
    }

    private fun computeBloomPass(source: ColorRenderTarget, brightnessThreshold: Float) {
        val target = requireNotNull(brightFramebuffer)
        val program = requireNotNull(brightnessProgram)
        PostProcessRenderer.render(program, source, RenderSurface(target.framebufferObject, target.width, target.height), Blending.Replace) {
            setFloat("threshold", brightnessThreshold)
            setFloat("intensity", 1.0F)
        }
    }

    private fun prepareDownsamplePass() {
        val source = requireNotNull(brightFramebuffer)
        val target = bloomDownFramebuffers.first()
        PostProcessRenderer.copy(source, RenderSurface(target.framebufferObject, target.width, target.height))
    }

    private fun generateDownsamplePasses() {
        prepareDownsamplePass()
        val program = requireNotNull(tentProgram)

        for (levelIndex in 1 until bloomDownFramebuffers.size) {
            val source = bloomDownFramebuffers[levelIndex - 1]
            val target = bloomDownFramebuffers[levelIndex]
            PostProcessRenderer.render(program, source, RenderSurface(target.framebufferObject, target.width, target.height), Blending.Replace) {
                setVector2("texelSize", 1.0F / source.width, 1.0F / source.height)
            }
        }
    }

    private fun prepareUpsamplePass() {
        val source = bloomDownFramebuffers.last()
        val target = bloomUpFramebuffers.last()
        PostProcessRenderer.copy(source, RenderSurface(target.framebufferObject, target.width, target.height))
    }

    private fun generateUpsamplePasses() {
        prepareUpsamplePass()
        val program = requireNotNull(tentProgram)

        for (levelIndex in bloomUpFramebuffers.lastIndex - 1 downTo 0) {
            val lowerResolution = bloomUpFramebuffers[levelIndex + 1]
            val target = bloomUpFramebuffers[levelIndex]
            PostProcessRenderer.copy(lowerResolution, RenderSurface(target.framebufferObject, target.width, target.height))

            val currentResolution = bloomDownFramebuffers[levelIndex]
            PostProcessRenderer.render(program, currentResolution, RenderSurface(target.framebufferObject, target.width, target.height), Blending.Additive) {
                setVector2("texelSize", 1.0F / currentResolution.width, 1.0F / currentResolution.height)
            }
        }
    }

    private fun generateMipmaps() {
        generateDownsamplePasses()
        generateUpsamplePasses()
    }

    private fun renderBloom(source: ColorRenderTarget, brightnessThreshold: Float): ManagedColorRenderTarget {
        clearBloomPasses()
        computeBloomPass(source, brightnessThreshold)
        generateMipmaps()
        return bloomUpFramebuffers.first()
    }

    private fun ensurePrograms() {
        if (brightnessProgram != null && tentProgram != null) return

        val newBrightnessProgram = BlitProgram(SCREEN_QUAD_VERTEX_SHADER, BRIGHTNESS_FRAGMENT_SHADER)
        try {
            val newTentProgram = BlitProgram(SCREEN_QUAD_VERTEX_SHADER, TENT_FRAGMENT_SHADER)
            brightnessProgram = newBrightnessProgram
            tentProgram = newTentProgram
        } catch (throwable: Throwable) {
            newBrightnessProgram.close()
            throw throwable
        }
    }

    private fun ensureFramebuffers(width: Int, height: Int) {
        val requestedDimensions = FramebufferDimensions(width, height)
        if (framebufferDimensions == requestedDimensions) return

        closeFramebuffers()
        val pyramidDimensions = bloomPyramidDimensions(width, height)
        try {
            brightFramebuffer = ManagedColorRenderTarget(width, height)
            contentFramebuffer = ManagedColorRenderTarget(width, height)
            pyramidDimensions.mapTo(bloomDownFramebuffers) { dimensions -> ManagedColorRenderTarget(dimensions.width, dimensions.height) }
            pyramidDimensions.mapTo(bloomUpFramebuffers) { dimensions -> ManagedColorRenderTarget(dimensions.width, dimensions.height) }
            framebufferDimensions = requestedDimensions
        } catch (throwable: Throwable) {
            closeFramebuffers()
            throw throwable
        }
    }

    private fun disablePipeline(message: String, failure: Throwable) {
        if (initializationFailed) return

        initializationFailed = true
        Logger.error(message, failure)
        val failedBrightnessProgram = brightnessProgram
        val failedTentProgram = tentProgram
        brightnessProgram = null
        tentProgram = null

        try {
            closeFramebuffers()
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
        }
        closeResource(failedBrightnessProgram, failure)
        closeResource(failedTentProgram, failure)
    }

    private fun closeResource(resource: AutoCloseable?, failure: Throwable) {
        if (resource == null) return

        try {
            resource.close()
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
        }
    }

    private fun closeFramebuffers() {
        brightFramebuffer?.close()
        contentFramebuffer?.close()
        bloomDownFramebuffers.forEach(ManagedColorRenderTarget::close)
        bloomUpFramebuffers.forEach(ManagedColorRenderTarget::close)
        brightFramebuffer = null
        contentFramebuffer = null
        bloomDownFramebuffers.clear()
        bloomUpFramebuffers.clear()
        framebufferDimensions = null
    }
}
