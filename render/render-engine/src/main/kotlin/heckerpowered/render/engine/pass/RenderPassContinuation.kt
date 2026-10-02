/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.command.pass.*

/**
 * Starts several passes that share attachments while applying the original initial load operations.
 *
 * Every attachment is stored for a later pass, and color resolves are deferred. This description
 * should be followed by [intermediateContinuationPass] when needed and a [finalContinuationPass]. Deriving
 * the description does not keep the selected attachments valid or synchronize their accesses.
 */
fun firstContinuationPass(description: RenderPassDescription, label: String): RenderPassDescription = description.derive(
    label = label,
    colorAttachments = description.colorAttachments.map { it?.storeForContinuation() },
    depthAttachment = description.depthAttachment?.storeForContinuation(),
    stencilAttachment = description.stencilAttachment?.storeForContinuation(),
    colorResolves = emptyList(),
)

/** Loads and stores every attachment between continuation passes without performing color resolves. */
fun intermediateContinuationPass(description: RenderPassDescription, label: String): RenderPassDescription = description.derive(
    label = label,
    colorAttachments = description.colorAttachments.map { it?.loadAndStoreForContinuation() },
    depthAttachment = description.depthAttachment?.loadAndStoreForContinuation(),
    stencilAttachment = description.stencilAttachment?.loadAndStoreForContinuation(),
    colorResolves = emptyList(),
)

/**
 * Finishes an attachment continuation by loading the preceding pass's stored results.
 *
 * The original store operations and color resolves are retained so they occur at the end of the
 * sequence. Pass the original description, rather than the description returned by
 * [firstContinuationPass], to retain those final operations.
 */
fun finalContinuationPass(description: RenderPassDescription, label: String): RenderPassDescription = description.derive(
    label = label,
    colorAttachments = description.colorAttachments.map { it?.loadForContinuation() },
    depthAttachment = description.depthAttachment?.loadForContinuation(),
    stencilAttachment = description.stencilAttachment?.loadForContinuation(),
)

private fun <T> RenderPassAttachment<T>.storeForContinuation(): RenderPassAttachment<T> =
    copy(operation = operation.copy(store = AttachmentStoreOperation.Store))

private fun <T> RenderPassAttachment<T>.loadForContinuation(): RenderPassAttachment<T> =
    copy(operation = operation.copy(load = AttachmentLoadOperation.Load))

private fun <T> RenderPassAttachment<T>.loadAndStoreForContinuation(): RenderPassAttachment<T> =
    copy(operation = AttachmentOperations.Default)
