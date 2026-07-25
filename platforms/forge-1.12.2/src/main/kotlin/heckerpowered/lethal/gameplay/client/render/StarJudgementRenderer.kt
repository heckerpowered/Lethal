/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.lethal.gameplay.common.entity.ForgeStarJudgementEntity
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.culling.ICamera
import net.minecraft.client.renderer.entity.Render
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.MathHelper
import org.lwjgl.opengl.GL11

internal class StarJudgementRenderer(renderManager: RenderManager) : Render<ForgeStarJudgementEntity>(renderManager) {
    override fun shouldRender(
        entity: ForgeStarJudgementEntity,
        camera: ICamera,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
    ): Boolean {
        return isWithinStarJudgementRenderDistance(entity.posX, entity.posZ, cameraX, cameraZ)
    }

    override fun doRender(
        entity: ForgeStarJudgementEntity,
        x: Double,
        y: Double,
        z: Double,
        entityYaw: Float,
        partialTicks: Float,
    ) {
        GlStateManager.alphaFunc(GL11.GL_GREATER, AlphaThreshold)
        bindTexture(TileEntityBeaconRenderer.TEXTURE_BEACON_BEAM)
        GlStateManager.disableFog()
        try {
            TileEntityBeaconRenderer.renderBeamSegment(
                x,
                y,
                z,
                partialTicks.toDouble(),
                TextureScale,
                entity.world.totalWorldTime.toDouble(),
                BeamStartOffset,
                BeamHeight,
                BeamColor,
                BeamRadius,
                GlowRadius,
            )
        } finally {
            GlStateManager.enableFog()
        }
    }

    override fun getEntityTexture(entity: ForgeStarJudgementEntity): ResourceLocation? {
        return null
    }

    private companion object {
        val BeamColor = floatArrayOf(1.0F, 1.0F, 1.0F)

        const val AlphaThreshold = 0.1F
        const val TextureScale = 1.0
        const val BeamStartOffset = -512
        const val BeamHeight = 1024
        const val BeamRadius = 0.2
        const val GlowRadius = 0.25
    }
}

internal fun isWithinStarJudgementRenderDistance(
    entityX: Double,
    entityZ: Double,
    cameraX: Double,
    cameraZ: Double,
): Boolean {
    val centeredEntityX = MathHelper.floor(entityX) + 0.5
    val centeredEntityZ = MathHelper.floor(entityZ) + 0.5
    val horizontalOffsetX = centeredEntityX - cameraX
    val horizontalOffsetZ = centeredEntityZ - cameraZ
    return horizontalOffsetX * horizontalOffsetX + horizontalOffsetZ * horizontalOffsetZ < RenderDistanceSquared
}

private const val RenderDistanceBlocks = 512.0
private const val RenderDistanceSquared = RenderDistanceBlocks * RenderDistanceBlocks
