/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity.damagesource

import heckerpowered.bridge.resources.Identifier
import java.util.*

sealed interface DamageType

data class VirtualDamageType(
    val type: Identifier,
    val features: EnumSet<DamageFeature>,
)

inline fun <reified E : Enum<E>> features(first: E, vararg rest: E): EnumSet<E> =
    EnumSet.of(first, *rest)