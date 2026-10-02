/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.math.AffineTransforms
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.pass.RasterPass
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.drawing.WorldDrawing
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.geometry.Matrix4
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderStageBuilderTest {
    @Test
    fun buildingSnapshotsDoesNotEndCollectionOrChangePreviousStages() {
        val builder = RenderStageBuilder()
        val collection = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {}
        val pass = RasterPass(
            RenderPassDescription("empty", renderArea = RenderArea(0, 0, 1, 1)),
            collection, ViewParameters.fromClipMatrix(Matrix4.Identity, 1, 1, CompareFunction.Always)
        )
        builder.rasterPass(pass)
        val first = builder.build()
        builder.rasterPass(pass)
        val second = builder.build()
        assertEquals(1, first.operations.size)
        assertEquals(2, second.operations.size)
    }
}
