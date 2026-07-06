/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge

interface BridgeRepresentation

/**
 * Bridge-owned, self-sufficient representation.
 *
 * Its semantics can be evaluated entirely in Bridge/common code.
 */
interface FreestandingRepresentation : BridgeRepresentation

/**
 * Host-owned representation that directly hosts a Bridge interface.
 *
 * The object is owned by the target platform/runtime and can be used as a Bridge
 * object without wrapping or copying.
 *
 * The host object may be a Minecraft native object, a platform subclass, or any
 * runtime object provided by the platform layer.
 */
interface HostingRepresentation : BridgeRepresentation

/**
 * Bridge-defined runtime representation.
 *
 * Its semantics are defined by Bridge rather than by fixed host runtime rules.
 * It may need platform interop to enter the host runtime pipeline.
 *
 * It is not necessarily self-sufficient like a freestanding value object.
 */
interface VirtualRepresentation : BridgeRepresentation

/**
 * A representation whose concrete semantic category is intentionally unresolved.
 *
 * It carries enough semantic intent to participate in Bridge APIs, but it cannot
 * be classified as a single precise backend representation until it reaches an
 * interop boundary capable of observing/materializing it.
 */
interface QuantumRepresentation : BridgeRepresentation