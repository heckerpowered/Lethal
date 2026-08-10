/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.vertex

/**
 * Defines how the bytes of a [VertexAttribute] are interpreted as a shader input value.
 *
 * Components of a format are stored consecutively without implicit padding. For example,
 * [Float32x3] consists of three consecutive 32-bit floating-point values and occupies 12 bytes,
 * while [Float32x4] occupies 16 bytes.
 *
 * Any padding between attributes is expressed explicitly by [VertexAttribute.offset] and
 * [VertexBufferLayout.stride].
 */
enum class VertexFormat(val sizeInBytes: Int) {
    /**
     * One 32-bit floating-point value.
     *
     * Typically used for scalar values such as weights, distances, or other numeric parameters.
     */
    Float32(4),

    /**
     * Two consecutive 32-bit floating-point values.
     *
     * Typically used for texture coordinates or other two-dimensional values.
     */
    Float32x2(8),

    /**
     * Three consecutive 32-bit floating-point values.
     *
     * Typically used for positions, normals, directions, or other three-dimensional vectors.
     */
    Float32x3(12),

    /**
     * Four consecutive 32-bit floating-point values occupying 16 bytes.
     *
     * Commonly used for colors, tangents, quaternions, or one row or column of a transformation
     * matrix.
     *
     * Colors stored as vertex attributes often use full floating-point components even when the final
     * framebuffer uses a lower-precision format such as RGBA8 or RGBA16. Vertex attributes describe
     * values used as shader inputs and are typically transformed and interpolated before reaching the
     * framebuffer, so representing them directly as floating-point values is convenient and preserves
     * intermediate precision.
     *
     * Framebuffer formats have a different tradeoff: they store values for potentially millions of
     * pixels and are read and written extensively during rendering, so reducing component precision
     * can substantially reduce memory usage and bandwidth.
     *
     * When vertex memory or fetch bandwidth matters more than precision, compact normalized formats
     * such as [Uint8x4Normalized] may also be used for colors.
     */
    Float32x4(16),

    /**
     * One unsigned 32-bit integer supplied to the shader as an integer value.
     *
     * Typically used for object, material, bone, or other integer identifiers.
     */
    Uint32(4),

    /**
     * Two consecutive unsigned 32-bit integers supplied to the shader as integer values.
     *
     * Typically used when an attribute contains a pair of integer identifiers or indices.
     */
    Uint32x2(8),

    /**
     * Three consecutive unsigned 32-bit integers supplied to the shader as integer values.
     */
    Uint32x3(12),

    /**
     * Four consecutive unsigned 32-bit integers supplied to the shader as integer values.
     *
     * Typically used for groups of indices such as skeletal-animation bone indices.
     */
    Uint32x4(16),

    /**
     * One signed 32-bit integer supplied to the shader as an integer value.
     *
     * Typically used for signed identifiers, discrete coordinates, or other integer data where
     * negative values are meaningful.
     */
    Sint32(4),

    /**
     * Three consecutive signed 32-bit integers supplied to the shader as integer values.
     */
    Sint32x3(12),


    /**
     * Four consecutive signed 32-bit integers supplied to the shader as integer values.
     */
    Sint32x4(16),

    /**
     * Two consecutive unsigned 8-bit integers converted to normalized floating-point values.
     *
     * Each stored component in the range `0..255` is supplied to the shader in the range
     * `0.0..1.0`. This is useful for compact values that do not require full floating-point
     * precision.
     */
    Uint8x2Normalized(2),

    /**
     * Four consecutive unsigned 8-bit integers converted to normalized floating-point values.
     *
     * Each stored component in the range `0..255` is supplied to the shader in the range
     * `0.0..1.0`. This is commonly used for compact vertex colors or skeletal-animation weights.
     */
    Uint8x4Normalized(4),
}