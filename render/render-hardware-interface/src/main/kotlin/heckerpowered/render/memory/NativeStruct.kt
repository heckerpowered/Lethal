/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

/**
 * Marks an interface whose properties describe one packed native-memory reservation.
 *
 * Every property must be an immutable abstract [NativeAddress] annotated with exactly one supported element layout.
 * The generated [MemoryFrame] overload reserves the complete structure in the current frame. Its [MemoryStack]
 * overload owns a temporary frame for callers that do not already have one. [alignment] sets the minimum alignment of
 * the complete structure and must be a positive power of two.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class NativeStruct(val alignment: Int = 1)

/**
 * Declares a field containing [count] native floats.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class FloatElements(val count: Int)

/**
 * Declares a field containing [count] native integers.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class IntElements(val count: Int)

/**
 * Declares a raw byte field with an explicit power-of-two [alignment].
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Bytes(val count: Int, val alignment: Int = 1)
