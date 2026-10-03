/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

/**
 * Describes one temporary native record that can be filled or queried through named fields.
 *
 * For an Example declaration, use the generated `stack.withExample { ... }` entry point. It
 * reserves the entire record once and supplies an ExampleView receiver with a MemoryFrame
 * context. Application code need not select a MemoryLayout, construct that view, or add offsets.
 * The frame is restored even if the callback throws or returns from its enclosing function.
 *
 * Declare a top-level public or internal interface with abstract val properties in source order.
 * Float and Int become readable/writable scalar properties. A [NativeAddress] field requires
 * one element annotation: a single numeric element becomes a scalar, larger fields become
 * bounded typed NIO buffers, and [Bytes] becomes a ByteBuffer. No contents are initialized.
 *
 * A property may also name another NativeStruct declared in the same compilation. Its complete
 * layout is embedded in the parent reservation, not separately allocated. Generated child views
 * expose their named fields and asByteBuffer(); homogeneous integer or float records also expose
 * asIntBuffer() or asFloatBuffer(). These views include declared query-capacity tails, so a native
 * API can receive the full required capacity without the caller reconstructing a byte range.
 *
 * Each member is aligned for its representation; [alignment] is a minimum for the whole record.
 * Nested fields use their own layout alignment and size. Cycles, inheritance, generic schemas,
 * and nested Kotlin declarations are rejected rather than assigning ambiguous layout rules.
 *
 * A MemoryFrame.withExample overload reserves within an existing frame without opening another
 * one. All generated views and buffers refer to the reservation's storage and must only be used
 * while that reservation is active.
 * The original allocExample address-callback overloads remain available for address-based native
 * APIs; they also reserve the entire structure once. Layout constants remain available for
 * diagnostics and interoperability, not routine field access.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class NativeStruct(val alignment: Int = 1)

/** Positive count of native floats. Shader-data declarations restrict this to their supported field shapes. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class FloatElements(val count: Int)

/** Positive count of native integers. Shader-data declarations restrict this to their supported field shapes. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class IntElements(val count: Int)

/** Raw host bytes; [count] is positive and [alignment] is a positive power of two. Not a shader-field encoding. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Bytes(val count: Int, val alignment: Int = 1)
