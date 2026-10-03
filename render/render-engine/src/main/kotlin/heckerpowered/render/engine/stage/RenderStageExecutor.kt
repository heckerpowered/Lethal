/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.GraphicsDevice
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
) : AutoCloseable {
    private val pendingLifetimes = ArrayList<ResourceLifetime>()
    private var completionRequired = false

    internal fun checkReady() {
        check(!completionRequired && pendingLifetimes.isEmpty()) { "Retry GPU completion before preparing another stage" }
    }

    /**
     * Waits for pending GPU work before closing every retained preparation lifetime.
     *
     * If the wait throws, the lifetimes remain available for retry and no allocations are released.
     * Execution remains blocked even when no preparation lifetimes are retained.
     */
    fun awaitIdle() {
        completionRequired = true
        device.awaitIdle()
        pendingLifetimes.forEach { it.close() }
        pendingLifetimes.clear()
        completionRequired = false
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
     *
     * Recording failure is rethrown after one completion attempt. A distinct completion failure is
     * attached to it when suppression is supported, except that a completion [Error] is propagated
     * with the recording failure attached. Recording Errors remain primary over non-Error completion
     * failures. Disabled suppression prevents attachment of the second diagnostic.
     */
    fun execute(stage: PreparedRenderStage) {
        checkReady()
        stage.temporaryLifetime.checkOpen()
        pendingLifetimes += stage.temporaryLifetime

        try {
            record(stage)
        } catch (recordingFailure: Throwable) {
            awaitIdleAfterFailedRecording(recordingFailure)
        }
        awaitIdle()
    }

    private fun awaitIdleAfterFailedRecording(recordingFailure: Throwable): Nothing {
        try {
            awaitIdle()
        } catch (completionFailure: Throwable) {
            if (completionFailure === recordingFailure) throw recordingFailure
            if (completionFailure is Error) {
                completionFailure.addSuppressed(recordingFailure)
                throw completionFailure
            }
            recordingFailure.addSuppressed(completionFailure)
        }
        throw recordingFailure
    }

    private fun record(stage: PreparedRenderStage) {
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
    }
}
