/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render.post

import heckerpowered.lethal.platform.render.ColorRenderTarget
import heckerpowered.lethal.platform.render.ManagedColorRenderTarget
import heckerpowered.lethal.platform.render.PostProcessRenderer
import heckerpowered.lethal.platform.render.RenderStateIsolation
import heckerpowered.lethal.platform.render.RenderSurface
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OpenGlHelper
import org.apache.logging.log4j.LogManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBTextureFloat
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GLContext

internal object BloomDiagnostics {
    private const val SAMPLE_DIAMETER_PIXELS = 17
    private const val MAXIMUM_OPENGL_ERRORS_PER_CHECK = 16
    private val Logger = LogManager.getLogger("Lethal Bloom Diagnostics")

    var Enabled = false

    private var capabilitiesLogged = false
    private var programsLogged = false
    private var lastUnsupportedState: String? = null
    private val loggedRequests = mutableSetOf<String>()
    private val loggedSuccesses = mutableSetOf<String>()
    private val sampledStages = mutableSetOf<String>()

    fun logSupportState(initializationFailed: Boolean, supported: Boolean) {
        if (!Enabled) return

        logCapabilities()
        if (supported) {
            lastUnsupportedState = null
            return
        }

        val supportState = "initializationFailed=$initializationFailed, openGL21=${OpenGlHelper.openGL21}, shadersSupported=${OpenGlHelper.areShadersSupported()}, framebufferSupported=${OpenGlHelper.framebufferSupported}, framebufferEnabled=${OpenGlHelper.isFramebufferEnabled()}"
        if (lastUnsupportedState == supportState) return

        lastUnsupportedState = supportState
        Logger.warn("Bloom diagnostics: Bloom is unavailable: $supportState")
    }

    fun logRequest(operation: String, source: ColorRenderTarget?, target: RenderSurface, depthRenderbuffer: Int?, brightnessThreshold: Float) {
        if (!Enabled || !loggedRequests.add(operation)) return

        val sourceDescription = source?.let(::describeTarget) ?: "none"
        Logger.info("Bloom diagnostics: first $operation request, source=$sourceDescription, targetFramebuffer=${target.framebufferObject}, targetSize=${target.width}x${target.height}, depthRenderbuffer=${depthRenderbuffer ?: "none"}, brightnessThreshold=$brightnessThreshold, boundFramebuffer=${currentFramebuffer()}, currentProgram=${currentProgram()}")
    }

    fun logProgramsReady(vertexShaderPath: String, brightnessShaderPath: String, tentShaderPath: String) {
        if (!Enabled || programsLogged) return

        programsLogged = true
        Logger.info("Bloom diagnostics: shader programs are ready, vertex=$vertexShaderPath, brightness=$brightnessShaderPath, tent=$tentShaderPath")
    }

    fun logFramebufferAllocation(brightTarget: ManagedColorRenderTarget, contentTarget: ManagedColorRenderTarget, downsampleTargets: List<ManagedColorRenderTarget>, upsampleTargets: List<ManagedColorRenderTarget>) {
        if (!Enabled) return

        Logger.info("Bloom diagnostics: allocated Bloom framebuffers, colorInternalFormat=${formatName(brightTarget.colorInternalFormat)}, bright=${describeTarget(brightTarget)}, content=${describeTarget(contentTarget)}")
        Logger.info("Bloom diagnostics: downsample pyramid=" + downsampleTargets.joinToString(" -> ", transform = ::describeTarget))
        Logger.info("Bloom diagnostics: upsample pyramid=" + upsampleTargets.joinToString(" -> ", transform = ::describeTarget))
    }

    fun checkOpenGlErrors(stage: String) {
        if (!Enabled) return

        try {
            var errorCount = 0
            var errorCode = GL11.glGetError()
            while (errorCode != GL11.GL_NO_ERROR && errorCount < MAXIMUM_OPENGL_ERRORS_PER_CHECK) {
                Logger.error("Bloom diagnostics: OpenGL error at $stage: ${errorName(errorCode)} ($errorCode), boundFramebuffer=${currentFramebuffer()}, currentProgram=${currentProgram()}")
                errorCount++
                errorCode = GL11.glGetError()
            }

            if (errorCode != GL11.GL_NO_ERROR) {
                Logger.error("Bloom diagnostics: more than $MAXIMUM_OPENGL_ERRORS_PER_CHECK OpenGL errors were present at $stage")
            }
        } catch (throwable: Throwable) {
            Logger.warn("Bloom diagnostics: failed to inspect OpenGL errors at $stage", throwable)
        }
    }

    fun sampleTarget(stage: String, target: ColorRenderTarget) {
        sampleSurface(stage, RenderSurface(target.framebufferObject, target.width, target.height))
    }

    fun sampleSurface(stage: String, surface: RenderSurface) {
        if (!Enabled || !sampledStages.add(stage)) return

        try {
            val sampleWidthPixels = minOf(SAMPLE_DIAMETER_PIXELS, surface.width)
            val sampleHeightPixels = minOf(SAMPLE_DIAMETER_PIXELS, surface.height)
            val sampleHorizontalPositionPixels = (surface.width - sampleWidthPixels) / 2
            val sampleVerticalPositionPixels = (surface.height - sampleHeightPixels) / 2
            val pixels = BufferUtils.createFloatBuffer(sampleWidthPixels * sampleHeightPixels * 4)
            var framebufferStatus = 0

            RenderStateIsolation.isolate {
                PostProcessRenderer.bind(surface)
                framebufferStatus = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)
                GL11.glReadPixels(sampleHorizontalPositionPixels, sampleVerticalPositionPixels, sampleWidthPixels, sampleHeightPixels, GL11.GL_RGBA, GL11.GL_FLOAT, pixels)
            }

            checkOpenGlErrors("$stage readback")
            val sample = brightestSample(pixels, sampleWidthPixels * sampleHeightPixels)
            Logger.info("Bloom diagnostics: $stage center sample, framebuffer=${surface.framebufferObject}, status=${framebufferStatusName(framebufferStatus)} ($framebufferStatus), region=${sampleWidthPixels}x$sampleHeightPixels, brightestRgb=${sample.brightness}, rgba=(${sample.red}, ${sample.green}, ${sample.blue}, ${sample.alpha}), nonFinitePixels=${sample.nonFinitePixelCount}")
        } catch (throwable: Throwable) {
            Logger.warn("Bloom diagnostics: failed to sample $stage", throwable)
        }
    }

    fun logSuccess(operation: String) {
        if (!Enabled || !loggedSuccesses.add(operation)) return

        Logger.info("Bloom diagnostics: $operation completed successfully")
    }

    fun logPipelineFailure(message: String) {
        if (!Enabled) return

        Logger.error("Bloom diagnostics: disabling the pipeline after '$message', boundFramebuffer=${currentFramebuffer()}, currentProgram=${currentProgram()}")
        checkOpenGlErrors(message)
    }

    private fun logCapabilities() {
        if (capabilitiesLogged) return
        capabilitiesLogged = true

        try {
            val capabilities = GLContext.getCapabilities()
            val minecraft = Minecraft.getMinecraft()
            val framebuffer = minecraft.framebuffer
            Logger.info("Bloom diagnostics: diagnostics are enabled")
            Logger.info("Bloom diagnostics: OpenGL vendor=${GL11.glGetString(GL11.GL_VENDOR)}, renderer=${GL11.glGetString(GL11.GL_RENDERER)}, version=${GL11.glGetString(GL11.GL_VERSION)}, shadingLanguage=${GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION)}")
            Logger.info("Bloom diagnostics: OpenGlHelper=${OpenGlHelper.getLogText()}")
            Logger.info("Bloom diagnostics: capabilities openGL21=${OpenGlHelper.openGL21}, shadersSupported=${OpenGlHelper.shadersSupported}, framebufferSupported=${OpenGlHelper.framebufferSupported}, framebufferEnabled=${OpenGlHelper.isFramebufferEnabled()}, framebufferSetting=${minecraft.gameSettings.fboEnable}, OpenGL30=${capabilities.OpenGL30}, ARBTextureFloat=${capabilities.GL_ARB_texture_float}, ARBColorBufferFloat=${capabilities.GL_ARB_color_buffer_float}, ARBFramebufferObject=${capabilities.GL_ARB_framebuffer_object}, EXTFramebufferObject=${capabilities.GL_EXT_framebuffer_object}, maximumTextureSize=${GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)}")
            Logger.info("Bloom diagnostics: Minecraft framebuffer object=${framebuffer.framebufferObject}, colorTexture=${framebuffer.framebufferTexture}, depthBuffer=${framebuffer.depthBuffer}, useDepth=${framebuffer.useDepth}, framebufferSize=${framebuffer.framebufferWidth}x${framebuffer.framebufferHeight}, textureSize=${framebuffer.framebufferTextureWidth}x${framebuffer.framebufferTextureHeight}, displaySize=${minecraft.displayWidth}x${minecraft.displayHeight}, boundFramebuffer=${currentFramebuffer()}, currentProgram=${currentProgram()}")
        } catch (throwable: Throwable) {
            Logger.warn("Bloom diagnostics: failed to collect OpenGL capabilities", throwable)
        }
    }

    private fun brightestSample(pixels: java.nio.FloatBuffer, pixelCount: Int): ColorSample {
        var maximumBrightness = Float.NEGATIVE_INFINITY
        var brightestRed = Float.NaN
        var brightestGreen = Float.NaN
        var brightestBlue = Float.NaN
        var brightestAlpha = Float.NaN
        var nonFinitePixelCount = 0

        for (pixelIndex in 0 until pixelCount) {
            val componentIndex = pixelIndex * 4
            val red = pixels.get(componentIndex)
            val green = pixels.get(componentIndex + 1)
            val blue = pixels.get(componentIndex + 2)
            val alpha = pixels.get(componentIndex + 3)
            if (!red.isFinite() || !green.isFinite() || !blue.isFinite() || !alpha.isFinite()) {
                nonFinitePixelCount++
                continue
            }

            val brightness = maxOf(red, green, blue)
            if (brightness <= maximumBrightness) continue

            maximumBrightness = brightness
            brightestRed = red
            brightestGreen = green
            brightestBlue = blue
            brightestAlpha = alpha
        }

        return ColorSample(maximumBrightness, brightestRed, brightestGreen, brightestBlue, brightestAlpha, nonFinitePixelCount)
    }

    private fun describeTarget(target: ColorRenderTarget): String {
        return "framebuffer=${target.framebufferObject},texture=${target.colorTexture},size=${target.width}x${target.height}"
    }

    private fun formatName(format: Int): String {
        return when (format) {
            ARBTextureFloat.GL_RGBA16F_ARB -> "GL_RGBA16F_ARB"
            GL11.GL_RGBA8 -> "GL_RGBA8"
            else -> "unknown($format)"
        }
    }

    private fun framebufferStatusName(status: Int): String {
        return when (status) {
            OpenGlHelper.GL_FRAMEBUFFER_COMPLETE -> "complete"
            OpenGlHelper.GL_FB_INCOMPLETE_ATTACHMENT -> "incomplete attachment"
            OpenGlHelper.GL_FB_INCOMPLETE_MISS_ATTACH -> "missing attachment"
            OpenGlHelper.GL_FB_INCOMPLETE_DRAW_BUFFER -> "incomplete draw buffer"
            OpenGlHelper.GL_FB_INCOMPLETE_READ_BUFFER -> "incomplete read buffer"
            else -> "unknown"
        }
    }

    private fun errorName(error: Int): String {
        return when (error) {
            GL11.GL_INVALID_ENUM -> "GL_INVALID_ENUM"
            GL11.GL_INVALID_VALUE -> "GL_INVALID_VALUE"
            GL11.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
            GL11.GL_STACK_OVERFLOW -> "GL_STACK_OVERFLOW"
            GL11.GL_STACK_UNDERFLOW -> "GL_STACK_UNDERFLOW"
            GL11.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
            GL30.GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
            else -> "unknown"
        }
    }

    private fun currentFramebuffer(): Int {
        return try {
            GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
        } catch (_: Throwable) {
            -1
        }
    }

    private fun currentProgram(): Int {
        return try {
            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        } catch (_: Throwable) {
            -1
        }
    }

    private data class ColorSample(val brightness: Float, val red: Float, val green: Float, val blue: Float, val alpha: Float, val nonFinitePixelCount: Int)
}
