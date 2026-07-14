/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import heckerpowered.bridge.adapter.client.render.RenderColor
import heckerpowered.bridge.math.VectorView
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OpenGlHelper
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.ARBColorBufferFloat
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GLContext

internal object WorldLineRenderer {
    private const val SPIKE_REACH_WIDTH_MULTIPLIER = 2.5F
    private const val EDGE_PADDING_PIXELS = 1.0F
    private const val ANIMATION_CYCLE_NANOSECONDS = 10_000_000_000L
    private val Logger = LogManager.getLogger("Lethal World Line Renderer")

    private var program: WorldLineProgram? = null
    private var shaderInitializationFailed = false

    fun drawLine(startPosition: VectorView, endPosition: VectorView, widthPixels: Float, color: RenderColor, lightningIntensity: Float, lightningSpikeDensity: Float, lightningAnimationFrequency: Float) {
        require(widthPixels.isFinite() && widthPixels > 0.0F) { "Line width must be finite and positive" }
        require(lightningIntensity.isFinite() && lightningIntensity >= 0.0F) { "Lightning intensity must be finite and non-negative" }
        require(lightningSpikeDensity.isFinite() && lightningSpikeDensity > 0.0F) { "Lightning spike density must be finite and positive" }
        require(lightningAnimationFrequency.isFinite() && lightningAnimationFrequency >= 0.0F) { "Lightning animation frequency must be finite and non-negative" }
        val renderManager = Minecraft.getMinecraft().renderManager
        val startCameraPosition = CameraRelativePosition(
            xCoordinate = startPosition.x - renderManager.viewerPosX,
            yCoordinate = startPosition.y - renderManager.viewerPosY,
            zCoordinate = startPosition.z - renderManager.viewerPosZ,
        )
        val endCameraPosition = CameraRelativePosition(
            xCoordinate = endPosition.x - renderManager.viewerPosX,
            yCoordinate = endPosition.y - renderManager.viewerPosY,
            zCoordinate = endPosition.z - renderManager.viewerPosZ,
        )

        RenderStateIsolation.isolate {
            preparePipeline(color)
            if (!drawElectricLine(startCameraPosition, endCameraPosition, widthPixels, lightningIntensity, lightningSpikeDensity, lightningAnimationFrequency)) {
                drawFallbackLine(startCameraPosition, endCameraPosition, widthPixels)
            }
        }
    }

    private fun preparePipeline(color: RenderColor) {
        allowHighDynamicRangeColor()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_ALPHA_TEST)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_FOG)
        GL11.glEnable(GL11.GL_BLEND)
        GL14.glBlendEquation(GL14.GL_FUNC_ADD)
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO)
        GL11.glEnable(GL11.GL_DEPTH_TEST)
        GL11.glDepthFunc(GL11.GL_LEQUAL)
        GL11.glDepthMask(false)
        GL11.glColorMask(true, true, true, true)
        GL11.glColor4f(color.red, color.green, color.blue, color.alpha)
    }

    private fun drawElectricLine(startPosition: CameraRelativePosition, endPosition: CameraRelativePosition, widthPixels: Float, lightningIntensity: Float, lightningSpikeDensity: Float, lightningAnimationFrequency: Float): Boolean {
        val currentProgram = lineProgram() ?: return false
        val minecraft = Minecraft.getMinecraft()
        val coreHalfWidthPixels = widthPixels * 0.5F
        val spikeReachPixels = widthPixels * SPIKE_REACH_WIDTH_MULTIPLIER * lightningIntensity
        val ribbonHalfWidthPixels = coreHalfWidthPixels + EDGE_PADDING_PIXELS + spikeReachPixels
        val animationTimeSeconds = (System.nanoTime() % ANIMATION_CYCLE_NANOSECONDS).toFloat() / 1_000_000_000.0F

        currentProgram.bind()
        currentProgram.setVector2("viewportSize", minecraft.framebuffer.framebufferWidth.toFloat(), minecraft.framebuffer.framebufferHeight.toFloat())
        currentProgram.setVector3("startPosition", startPosition.xCoordinate.toFloat(), startPosition.yCoordinate.toFloat(), startPosition.zCoordinate.toFloat())
        currentProgram.setVector3("endPosition", endPosition.xCoordinate.toFloat(), endPosition.yCoordinate.toFloat(), endPosition.zCoordinate.toFloat())
        currentProgram.setFloat("coreHalfWidthPixels", coreHalfWidthPixels)
        currentProgram.setFloat("ribbonHalfWidthPixels", ribbonHalfWidthPixels)
        currentProgram.setFloat("spikeReachPixels", spikeReachPixels)
        currentProgram.setFloat("lightningIntensity", lightningIntensity)
        currentProgram.setFloat("lightningSpikeDensity", lightningSpikeDensity)
        currentProgram.setFloat("lightningAnimationFrequency", lightningAnimationFrequency)
        currentProgram.setFloat("animationTimeSeconds", animationTimeSeconds)

        GL11.glBegin(GL11.GL_QUADS)
        vertex(startPosition, 0.0F, -1.0F)
        vertex(endPosition, 1.0F, -1.0F)
        vertex(endPosition, 1.0F, 1.0F)
        vertex(startPosition, 0.0F, 1.0F)
        GL11.glEnd()
        return true
    }

    private fun vertex(position: CameraRelativePosition, positionAlongLine: Float, side: Float) {
        GL11.glTexCoord2f(positionAlongLine, side)
        GL11.glVertex3d(position.xCoordinate, position.yCoordinate, position.zCoordinate)
    }

    private fun drawFallbackLine(startPosition: CameraRelativePosition, endPosition: CameraRelativePosition, widthPixels: Float) {
        GL20.glUseProgram(0)
        GL11.glEnable(GL11.GL_LINE_SMOOTH)
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST)
        GL11.glLineWidth(widthPixels)
        GL11.glBegin(GL11.GL_LINES)
        GL11.glVertex3d(startPosition.xCoordinate, startPosition.yCoordinate, startPosition.zCoordinate)
        GL11.glVertex3d(endPosition.xCoordinate, endPosition.yCoordinate, endPosition.zCoordinate)
        GL11.glEnd()
    }

    private fun lineProgram(): WorldLineProgram? {
        if (shaderInitializationFailed || !OpenGlHelper.openGL21 || !OpenGlHelper.areShadersSupported()) return null
        program?.let { return it }

        return try {
            WorldLineProgram().also { program = it }
        } catch (throwable: Throwable) {
            shaderInitializationFailed = true
            Logger.error("Failed to initialize the electric line shader; using the plain line fallback", throwable)
            null
        }
    }

    private fun allowHighDynamicRangeColor() {
        if (!GLContext.getCapabilities().GL_ARB_color_buffer_float) return
        ARBColorBufferFloat.glClampColorARB(ARBColorBufferFloat.GL_CLAMP_VERTEX_COLOR_ARB, GL11.GL_FALSE)
        ARBColorBufferFloat.glClampColorARB(ARBColorBufferFloat.GL_CLAMP_FRAGMENT_COLOR_ARB, ARBColorBufferFloat.GL_FIXED_ONLY_ARB)
    }
}

private data class CameraRelativePosition(val xCoordinate: Double, val yCoordinate: Double, val zCoordinate: Double)
