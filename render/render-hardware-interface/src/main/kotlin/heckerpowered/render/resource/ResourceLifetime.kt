/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource

import heckerpowered.render.terminateOnFailure
import heckerpowered.render.withFailureCleanup

/**
 * Closes a group of resources together when this lifetime ends.
 *
 * Resources close in reverse registration order on the calling thread. Use [build] to also
 * close them when construction fails. A successful result must retain this lifetime and
 * arrange its [close] at the enclosing lifecycle boundary; garbage collection does not do it.
 *
 * Register each resource once, and do not register shared resources managed elsewhere.
 * Other references do not extend this lifetime. Callers must satisfy each resource's closing
 * requirements; this class neither selects a thread nor waits for pending work. Cleanup
 * failure is fatal, as at the project's other destruction boundaries.
 */
class ResourceLifetime private constructor() : AutoCloseable {
    private var resources: ArrayList<AutoCloseable>? = ArrayList()

    fun checkOpen() {
        check(resources != null) { "Resource lifetime is closed" }
    }

    /** Registers a newly created resource for closing. Failed registration closes it immediately. */
    fun <T : AutoCloseable> register(resource: T): T {
        // Creation has already succeeded. Registration can still fail, including with an
        // allocation Error, before the resource is present in the cleanup list.
        return withFailureCleanup({
            checkNotNull(resources) { "Resource lifetime is closed" }.add(resource)
            resource
        }) { resource.close() }
    }

    override fun close() = terminateOnFailure {
        val registered = resources ?: return@terminateOnFailure
        resources = null
        for (index in registered.lastIndex downTo 0) registered[index].close()
    }

    companion object {
        fun <R> build(create: ResourceLifetime.() -> R): R {
            val lifetime = ResourceLifetime()
            return withFailureCleanup({ lifetime.create() }) { lifetime.close() }
        }
    }
}

/** Binds this resource's release to [lifetime] and returns the same resource. */
fun <T : AutoCloseable> T.lifetime(lifetime: ResourceLifetime): T = lifetime.register(this)
