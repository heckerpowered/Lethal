/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

/**
 * Reports a failure to establish backend layout state after the interface request was accepted.
 *
 * For example, allocation of a native layout or its supporting state may fail. Unsupported
 * interface requirements are reported as UnsupportedOperationException instead, so callers can
 * distinguish a missing capability from failure to establish an otherwise supported request.
 */
class PipelineLayoutCreationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
