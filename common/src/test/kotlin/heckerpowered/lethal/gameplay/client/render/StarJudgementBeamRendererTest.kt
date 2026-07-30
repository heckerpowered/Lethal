/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.render.PushConstantField
import heckerpowered.render.ShaderField
import heckerpowered.render.ShaderStage
import heckerpowered.render.ShaderValueType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class StarJudgementBeamRendererTest {
    @Test
    fun generatedShaderLayoutsMatchThePipelineContract() {
        assertEquals(expected = 64, actual = StarJudgementBeamSceneLayout.sizeBytes)
        assertEquals(expected = listOf(ShaderField("modelViewProjectionMatrix", ShaderValueType.Matrix4, 0)), actual = StarJudgementBeamSceneLayout.fields)
        assertEquals(
            expected = listOf(
                PushConstantField("beamOrigin", ShaderValueType.Float3, 0, setOf(ShaderStage.Vertex)),
                PushConstantField("firstCorner", ShaderValueType.Float2, 16, setOf(ShaderStage.Vertex)),
                PushConstantField("secondCorner", ShaderValueType.Float2, 24, setOf(ShaderStage.Vertex)),
                PushConstantField("verticalRange", ShaderValueType.Float2, 32, setOf(ShaderStage.Vertex)),
                PushConstantField("textureVRange", ShaderValueType.Float2, 40, setOf(ShaderStage.Vertex)),
                PushConstantField("color", ShaderValueType.Float4, 48, setOf(ShaderStage.Vertex)),
            ),
            actual = StarJudgementBeamFaceConstantsLayout.fields,
        )
        assertEquals(expected = 64, actual = StarJudgementBeamFaceConstantsLayout.sizeBytes)
    }

    @Test
    fun initialGeometryMatchesTheLegacyBeaconBeam() {
        val geometry = createStarJudgementBeamGeometry(0.0)
        val firstInnerFace = geometry.innerFaces.first()
        val firstGlowFace = geometry.glowFaces.first()

        assertEquals(expected = 4, actual = geometry.innerFaces.size)
        assertEquals(expected = 4, actual = geometry.glowFaces.size)
        assertEquals(expected = 0.5 + cos(Math.PI * 0.75) * 0.2, actual = firstInnerFace.firstCorner.x)
        assertEquals(expected = 0.5 + sin(Math.PI * 0.75) * 0.2, actual = firstInnerFace.firstCorner.z)
        assertEquals(expected = 0.5 + cos(Math.PI * 0.25) * 0.2, actual = firstInnerFace.secondCorner.x)
        assertEquals(expected = 0.5 + sin(Math.PI * 0.25) * 0.2, actual = firstInnerFace.secondCorner.z)
        assertEquals(expected = StarJudgementBeamRange(-512.0, 512.0), actual = firstInnerFace.verticalRange)
        assertEquals(expected = StarJudgementBeamRange(-1.0, 2559.0), actual = firstInnerFace.textureVRange)
        assertEquals(expected = 1.0F, actual = firstInnerFace.alpha)
        assertEquals(expected = StarJudgementBeamCorner(0.25, 0.25), actual = firstGlowFace.firstCorner)
        assertEquals(expected = StarJudgementBeamCorner(0.75, 0.25), actual = firstGlowFace.secondCorner)
        assertEquals(expected = StarJudgementBeamRange(-1.0, 1023.0), actual = firstGlowFace.textureVRange)
        assertEquals(expected = 0.125F, actual = firstGlowFace.alpha)
    }

    @Test
    fun animationRotatesTheCoreAndScrollsBothTextureLayers() {
        val geometry = createStarJudgementBeamGeometry(1.0)
        val firstInnerFace = geometry.innerFaces.first()
        val firstGlowFace = geometry.glowFaces.first()
        val expectedAngle = Math.PI * 0.75 - 0.0375

        assertEquals(expected = 0.5 + cos(expectedAngle) * 0.2, actual = firstInnerFace.firstCorner.x)
        assertEquals(expected = 0.5 + sin(expectedAngle) * 0.2, actual = firstInnerFace.firstCorner.z)
        assertEquals(expected = -0.2, actual = firstInnerFace.textureVRange.start, absoluteTolerance = 1.0E-12)
        assertEquals(expected = 2559.8, actual = firstInnerFace.textureVRange.end, absoluteTolerance = 1.0E-12)
        assertEquals(expected = -0.2, actual = firstGlowFace.textureVRange.start, absoluteTolerance = 1.0E-12)
        assertEquals(expected = 1023.8, actual = firstGlowFace.textureVRange.end, absoluteTolerance = 1.0E-12)
    }
}
