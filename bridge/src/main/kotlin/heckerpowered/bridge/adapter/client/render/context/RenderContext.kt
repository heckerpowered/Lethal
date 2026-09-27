/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render.context

import heckerpowered.bridge.math.VectorView
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.geometry.Matrix4
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.resource.target.RenderTarget

/**
 * The host environment shared by renderers during one rendering stage.
 *
 * A context provides the destination and stage-wide state, not a command stream or ownership
 * of host resources. Supply [CommandEncoder] separately when recording. The context and its
 * borrowed targets must remain valid throughout the stage and must not be retained by renderers.
 * Creating a Kotlin context scope does not make a native graphics context current.
 *
 * World and UI contexts supply their different coordinate transforms. Effect-specific assets
 * and per-object inputs do not belong in this shared environment.
 */
interface RenderContext {
    /** The host-provided device. The stage's encoder and all resources must belong to this device. */
    val graphicsDevice: GraphicsDevice

    /** The host's main frame target; it is not necessarily the destination of this stage. */
    val mainRenderTarget: RenderTarget

    /**
     * Destination selected for this stage. Ordinary stages use [mainRenderTarget]; offscreen
     * stages can override this without changing each renderer's drawing arguments. This is a
     * fixed attachment selection for the stage, not an instruction to query the currently bound
     * native framebuffer. Individual post-processing passes may still choose other targets.
     */
    val renderTarget: RenderTarget
        get() = mainRenderTarget

    /**
     * Optional stage region, such as a split-screen view. Null lets each pass infer its complete
     * common attachment extent. A stage projection must match this region or the inferred extent.
     * This selects pass effects and its initial viewport; per-draw clipping remains a scissor.
     */
    val renderArea: RenderArea?
        get() = null
}

/** World-stage state shared by world renderers; object positions and animation phases remain draw inputs. */
interface WorldRenderContext : RenderContext {
    /** Interpolated camera position in world space, for distance and camera-relative calculations. */
    val cameraPosition: VectorView

    /**
     * World-to-clip transformation using the RHI's clip coordinates and depth interval [0, 1].
     * It includes the view's camera translation. A renderer using this matrix with world-space
     * positions must not subtract [cameraPosition] again. Per-object model transforms remain
     * the renderer's responsibility.
     */
    val viewProjectionMatrix: Matrix4

    /**
     * Comparison consistent with this projection and the destination's existing depth contents.
     * The default uses conventional depth; a reverse-depth stage must override it accordingly.
     * Whether a particular effect writes depth is still that effect's pipeline choice.
     */
    val depthCompare: CompareFunction
        get() = CompareFunction.LessOrEqual
}

/** UI-stage state. The projection describes the stage region in the UI's own coordinate system. */
interface UiRenderContext : RenderContext {
    val projectionMatrix: Matrix4
}

/**
 * Post-processing environment. Inputs and intermediate targets belong to each effect or pass;
 * [renderTarget] is only the stage's default destination, not a shared collection of effect data.
 */
interface PostProcessContext : RenderContext
