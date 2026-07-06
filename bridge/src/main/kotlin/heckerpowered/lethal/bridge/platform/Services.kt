/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.platform

import heckerpowered.lethal.bridge.platform.services.ModPlatform
import java.util.*

object Services {
    val Platform = load<ModPlatform>()

    fun <T : Any> load(type: Class<T>): T {
        return loadOrNull(type)
            ?: error("Failed to load service for ${type.name}")
    }

    fun <T : Any> loadOrNull(type: Class<T>): T? {
        return ServiceLoader.load(type, Services::class.java.classLoader)
            .firstOrNull()
    }
}

inline fun <reified T : Any> Services.load(): T =
    load(T::class.java)

inline fun <reified T : Any> Services.loadOrNull(): T? =
    loadOrNull(T::class.java)