/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource

/**
 * Owns a backend resource whose [close] performs final release.
 *
 * Callers may close the resource only after all recorded references have been released or
 * abandoned and every submitted native or GPU use has actually completed. Returning from an
 * encoder callback or [heckerpowered.render.GraphicsDevice.encode] does not establish either condition.
 *
 * Closing does not wait for completion, flush or submit commands, or defer destruction. The
 * caller must arrange completion and end all uses before closing the resource.
 *
 * Before native destruction, backends must check the recorded and pending references they
 * track. A remaining reference violates the destruction precondition and must terminate through
 * [heckerpowered.render.terminateOnFailure], as must any cleanup failure; no failure may escape [close]. These checks
 * cannot prove safety for host or native references outside the backend's tracking. Ensuring
 * those references no longer use the resource remains the caller's responsibility.
 */
interface GpuResource : AutoCloseable