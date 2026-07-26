/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter

import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess

/**
 * Marks a host-neutral type exposed through the bridge access model.
 *
 * Access interfaces describe stable type relationships, not individual gameplay use cases. State
 * and behavior shared by every supported version of the same concept belong directly to that
 * concept's base access interface.
 *
 * A capability should be split into another access interface only when supported host versions
 * disagree about where the capability is exposed, which concrete host types provide it, or how it
 * must be reached. A single caller needing an operation, or an operation being convenient to
 * isolate, is not sufficient reason to create another access interface.
 *
 * A split capability must still extend the narrowest stable owner common to its valid hosts. For
 * example, [EntityEquipmentAccess] remains separate because equipment access varies by host, but it
 * extends [LivingEntityAccess] because equipment still belongs to a living entity. A capability
 * should extend [BridgeAccess] directly only when no narrower stable owner exists.
 *
 * This marker intentionally declares no behavior. It records the boundary and the design contract;
 * it does not imply that every access interface should inherit it directly.
 */
interface BridgeAccess

/**
 * Resolves a bridge view that the caller requires this value to expose.
 */
inline fun <reified View : BridgeAccess> BridgeAccess.asView(): View = this as View
