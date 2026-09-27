/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.render.terminateOnFailure

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
internal class ResourceLifetime private constructor() : AutoCloseable {
    private var resources: ArrayList<AutoCloseable>? = ArrayList()

    fun checkOpen() {
        check(resources != null) { "Resource lifetime is closed" }
    }

    /** Registers a newly created resource for closing. Failed registration closes it immediately. */
    fun <T : AutoCloseable> register(resource: T): T {
        // Creation has already succeeded. Registration can still fail, including with an
        // allocation Error, before the resource is present in the cleanup list.
        var registered = false
        try {
            checkNotNull(resources) { "Resource lifetime is closed" }.add(resource)
            registered = true
            return resource
        } finally {
            if (!registered) terminateOnFailure { resource.close() }
        }
    }

    override fun close() = terminateOnFailure {
        val registered = resources ?: return@terminateOnFailure
        resources = null
        for (index in registered.lastIndex downTo 0) registered[index].close()
    }

    companion object {
        fun <R> build(create: ResourceLifetime.() -> R): R {
            val lifetime = ResourceLifetime()
            // Only a fully constructed result can retain the lifetime for later closing.
            // Finally also covers Error exits without catching Throwable outside the fatal boundary.
            var constructed = false
            try {
                val result = lifetime.create()
                constructed = true
                return result
            } finally {
                if (!constructed) lifetime.close()
            }
        }
    }
}
