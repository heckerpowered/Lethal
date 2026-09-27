/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader.data

import heckerpowered.render.memory.FloatElements
import heckerpowered.render.memory.IntElements
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * Describes a std140 parameter block whose generated writer fills native bytes by field name.
 *
 * For example, six scalar float fields occupy offsets 0, 4, 8, 12, 16, and 20. The generated
 * block occupies 32 bytes after std140 structure padding. Assigning a writer property stores
 * its value at the matching offset; callers do not repeat these offsets at every upload.
 *
 * Use a top-level public or internal interface with abstract val properties, in declaration order.
 * Scalar Float and Int are accepted directly. [NativeAddress] properties need [FloatElements] or
 * [IntElements]: counts 1–4 denote one scalar/vector, not an array, and sixteen floats denote a
 * column-major mat4. Arrays, nested structures, and other shapes are rejected, not guessed.
 *
 * For a declaration named Example, KSP generates ExampleMemoryLayout, ExampleLayout (a host
 * MemoryLayout), ExampleWriter, and withExample frame/stack helpers. Scalar writer properties
 * are write-only; vectors use setField(x, y, ...). Matrices accept a Matrix4 property assignment
 * or setField(columnMajorValues) for a sixteen-element FloatArray.
 * Only padding is zeroed. Every field must be initialized before its bytes are consumed.
 *
 * CommandEncoder.writeExample(destination, write) fills one temporary block, then calls
 * writeBuffer after the writer returns normally. [GpuBufferView] must select exactly the generated
 * size and allow TransferDestination access. It does not allocate GPU storage, choose a descriptor
 * slot, or return a temporary uniform binding. Shader declarations and buffer bindings must
 * separately agree with this byte layout; this annotation does not compile or rewrite shaders.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GpuBufferData
