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
 * Every execution attempts to wait for GPU completion, even if recording fails. If the wait
 * fails, the stage's resources remain retained and new work is blocked until [awaitIdle] succeeds.
 * [close] releases resources without waiting, so callers must establish GPU completion first.
 */
internal class RenderStageExecutor(private val device: GraphicsDevice) : AutoCloseable {
    /** A failed wait can be pending even when there are no resources to retire. */
    private class PendingCompletion(val lifetime: ResourceLifetime? = null)

    private var pendingCompletion: PendingCompletion? = null

    internal fun checkReady() = check(pendingCompletion == null) { "Retry GPU completion before preparing another stage" }

    /**
     * Records a stage while retaining its temporary resources until GPU completion.
     *
     * Reusing a stage after its lifetime was released fails before recording starts. A recording
     * failure is propagated after completion has been attempted; if completion also fails, both
     * errors are preserved, with a completion [Error] taking precedence.
     */
    fun execute(stage: PreparedRenderStage) {
        withGpuWork(stage.temporaryLifetime) { record(stage) }
        for (completion in stage.completions) completion.complete()
    }

    private inline fun withGpuWork(lifetime: ResourceLifetime, action: () -> Unit) {
        retainUntilIdle(lifetime)

        val recording = runCatching { action() }
        val completion = runCatching { awaitIdle() }

        propagateFailure(recording.exceptionOrNull(), completion.exceptionOrNull())
    }

    private fun retainUntilIdle(lifetime: ResourceLifetime) {
        checkReady()
        lifetime.checkOpen()
        pendingCompletion = PendingCompletion(lifetime)
    }

    /**
     * Waits for GPU completion and releases retained resources only after the wait succeeds.
     *
     * On failure, the pending completion (possibly without a lifetime) remains available for
     * retry; no subsequent stage may be prepared until completion is confirmed.
     */
    fun awaitIdle() {
        val completion = pendingCompletion ?: PendingCompletion()
        pendingCompletion = completion

        device.awaitIdle()
        completion.lifetime?.close()
        pendingCompletion = null
    }

    /** GPU completion must already be established before final destruction. */
    override fun close() = terminateOnFailure {
        pendingCompletion?.lifetime?.close()
        pendingCompletion = null
    }

    /**
     * Preserves both failures when recording and completion fail.
     * Completion [Error]s take precedence; otherwise the recording failure stays primary.
     */
    private fun propagateFailure(recording: Throwable?, completion: Throwable?) {
        if (recording != null && completion != null && recording !== completion) {
            if (completion is Error) {
                completion.addSuppressed(recording)
            } else {
                recording.addSuppressed(completion)
            }
        }

        if (completion is Error) throw completion
        if (recording != null) throw recording
        if (completion != null) throw completion
    }

    private fun record(stage: PreparedRenderStage) {
        device.encode("render-engine") {
            stage.operations.forEach { it.encode(this) }
        }
    }
}
