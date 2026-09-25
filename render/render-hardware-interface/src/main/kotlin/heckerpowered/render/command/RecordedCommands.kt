/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.command

import heckerpowered.render.binding.DescriptorSet
import heckerpowered.render.buffer.GpuBufferView
import heckerpowered.render.pass.RenderPassDescription
import heckerpowered.render.pass.ScissorRectangle
import heckerpowered.render.pass.Viewport
import heckerpowered.render.pipeline.RenderPipeline
import heckerpowered.render.pipeline.primitive.IndexFormat
import heckerpowered.render.shader.ShaderStage
import java.util.*

/**
 * Supplies a completed logical command sequence to a backend before it starts GPU execution.
 *
 * The backend can inspect a scene pass followed by resolve and discard before choosing native
 * pass boundaries. This is why commands are inspectable values, not delayed user callbacks:
 * rerunning a callback could read changed application state, reuse expired addresses, or repeat
 * unrelated side effects. All application callbacks have already run when this value is returned.
 *
 * Command order, resource selections, and saved host inputs are fixed. Referenced GPU contents
 * are not frozen: a copy reads at its execution position. Resource references neither acquire
 * application ownership nor permit closing resources before their required use has ended.
 *
 * This is an implementation representation, not a reusable public command-buffer allocation.
 * [GraphicsRecording] prepares and submits each sequence once. The backend still validates
 * resource identity, access scopes, shader compatibility, native support, aliases, and dependencies.
 */
class RecordedCommands internal constructor(
    val label: String,
    commands: List<Command>,
) {
    val commands: List<Command> = Collections.unmodifiableList(ArrayList(commands))

    sealed interface Command

    class WriteBuffer internal constructor(val destination: GpuBufferView, val bytes: RecordedBytes) : Command
    class CopyBuffer internal constructor(val source: GpuBufferView, val destination: GpuBufferView) : Command

    /** The saved rows are tightly packed, even if the original host source had padding. */
    class WriteTexture internal constructor(val destination: ImageRegion, val bytes: RecordedBytes) : Command {
        val layout: TextureDataLayout get() = TextureDataLayout.TightlyPacked
    }

    class CopyBufferToTexture internal constructor(val source: GpuBufferView, val destination: ImageRegion, val layout: TextureDataLayout) : Command
    class CopyTextureToBuffer internal constructor(val source: ImageRegion, val destination: GpuBufferView, val layout: TextureDataLayout) : Command
    class CopyTexture internal constructor(val source: ImageRegion, val destination: ImageRegion) : Command
    class Resolve internal constructor(val source: ImageRegion, val destination: ImageRegion) : Command
    class DiscardContents internal constructor(val region: ImageRegion) : Command

    /**
     * Preserves a logical pass, including load/store effects even when it contains no draws.
     *
     * Commands retain their order inside this pass. Ending it does not require ending a native
     * rendering scope; lowering may combine adjacent operations only when their behavior permits it.
     */
    class Pass internal constructor(val description: RenderPassDescription, commands: List<PassCommand>) : Command {
        val commands: List<PassCommand> = Collections.unmodifiableList(ArrayList(commands))
    }

    sealed interface PassCommand

    class BindPipeline internal constructor(val pipeline: RenderPipeline) : PassCommand
    class BindVertexBuffer internal constructor(val slot: Int, val view: GpuBufferView) : PassCommand
    class BindIndexBuffer internal constructor(val view: GpuBufferView, val format: IndexFormat) : PassCommand
    class BindDescriptorSet internal constructor(val set: Int, val descriptors: DescriptorSet) : PassCommand
    class SetStencilReference internal constructor(val reference: UByte) : PassCommand

    class PushConstants internal constructor(stages: Set<ShaderStage>, val bytes: RecordedBytes, val destinationOffsetBytes: Int) : PassCommand {
        val stages: Set<ShaderStage> = Collections.unmodifiableSet(LinkedHashSet(stages))
    }

    /** Region values belong to this draw, not to the scope state after recording has ended. */
    class Draw internal constructor(
        val vertexCount: Int,
        val firstVertex: Int,
        val instanceCount: Int,
        val firstInstance: Int,
        val viewport: Viewport,
        val scissor: ScissorRectangle,
    ) : PassCommand

    class DrawIndexed internal constructor(
        val indexCount: Int,
        val firstIndex: Int,
        val baseVertex: Int,
        val instanceCount: Int,
        val firstInstance: Int,
        val viewport: Viewport,
        val scissor: ScissorRectangle,
    ) : PassCommand
}
