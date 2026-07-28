/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.platform

import heckerpowered.bridge.network.PayloadTransport
import heckerpowered.bridge.platform.services.ClientPlatform
import heckerpowered.bridge.platform.services.Entrypoint
import heckerpowered.bridge.platform.services.ModPlatform
import heckerpowered.bridge.platform.services.PlatformLogger
import java.util.*

object Services {
    val Logger by lazy { load<PlatformLogger>() }
    val Platform by lazy { load<ModPlatform>() }
    val ClientPlatform by lazy { load<ClientPlatform>() }
    val PayloadTransport by lazy { load<PayloadTransport>() }

    fun <T : Any> load(type: Class<T>): T {
        return loadOrNull(type) ?: error("Failed to load service for ${type.name}")
    }

    fun <T : Any> loadOrNull(type: Class<T>): T? {
        return ServiceLoader.load(type, Services::class.java.classLoader)
            .firstOrNull()
    }

    fun <T : Any> loads(type: Class<T>): ServiceLoader<T> {
        return ServiceLoader.load(type, Services::class.java.classLoader)
    }

    inline fun <reified T : Entrypoint> callEntrypoints() {
        val entrypoints = Services.loads<T>().toList()
        val entrypointCount = entrypoints.size

        Logger.info("Found $entrypointCount entrypoint(s).")
        for ((index, entrypoint) in entrypoints.withIndex()) {
            Logger.info("Calling entrypoint (${index + 1}/$entrypointCount): ${entrypoint.javaClass.name}")
            entrypoint.onEntrypoint()
        }
    }
}

inline fun <reified T : Any> Services.load(): T =
    load(T::class.java)

inline fun <reified T : Any> Services.loadOrNull(): T? =
    loadOrNull(T::class.java)

inline fun <reified T : Any> Services.loads(): ServiceLoader<T> =
    loads(T::class.java)
