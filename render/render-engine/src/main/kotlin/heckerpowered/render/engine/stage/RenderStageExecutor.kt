/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.prepare.GpuQuiescence
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.terminateOnFailure

/**
 * Records a prepared stage and retires its temporary resources after GPU completion.
 *
 * Uploads are recorded before their pass, and operations retain declaration order. A pass with
 * no draws still executes because its attachment operations may have observable effects.
 *
 * The executor waits after both successful and exceptional recording. A failed wait retains
 * pending lifetimes and prevents another execution until [awaitIdle] succeeds. Final [close]
 * releases allocations without waiting and therefore requires completion to be established first.
 */
internal class RenderStageExecutor(
    private val device: GraphicsDevice,
    private val quiescence: GpuQuiescence,
) : AutoCloseable {
    private val pendingLifetimes = ArrayList<ResourceLifetime>()

    internal fun checkReady() {
        check(pendingLifetimes.isEmpty()) { "Retry GPU completion before preparing another stage" }
    }

    /**
     * Waits for pending GPU work before closing every retained preparation lifetime.
     *
     * If the wait throws, the lifetimes remain available for retry and no allocations are released.
     */
    fun awaitIdle() {
        quiescence.awaitIdle()
        pendingLifetimes.forEach { it.close() }
        pendingLifetimes.clear()
    }

    /** GPU completion must already be established before final destruction. */
    override fun close() = terminateOnFailure {
        pendingLifetimes.forEach { it.close() }
        pendingLifetimes.clear()
    }

    /**
     * Records this stage while its temporary lifetime is still open.
     *
     * The lifetime is registered for retirement before recording starts, including when recording
     * later fails. Reusing a stage whose lifetime has been released fails before any GPU commands.
     */
    fun execute(stage: PreparedRenderStage) {
        checkReady()
        stage.temporaryLifetime.checkOpen()
        pendingLifetimes += stage.temporaryLifetime
        try {
            device.encode("render-engine") {
                for (operation in stage.operations) when (operation) {
                    is PreparedStageOperation.ImageTransfer -> operation.transfer.encode(this)
                    is PreparedStageOperation.CopyBuffer -> copyBuffer(operation.source, operation.destination)
                    is PreparedStageOperation.Pass -> {
                        val prepared = operation.pass
                        prepared.uploads.forEach { it.encode(this) }
                        // Empty draws can still clear, store, or resolve attachments.
                        renderPass(prepared.description, prepared.resources) {
                            prepared.encode(this)
                        }
                    }
                }
            }
        } finally {
            // Blocking, bounded-memory fallback. Replace only with a real completion-backed retirement service.
            awaitIdle()
        }
    }
}
