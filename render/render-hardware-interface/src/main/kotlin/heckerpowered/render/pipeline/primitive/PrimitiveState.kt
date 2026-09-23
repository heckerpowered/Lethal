/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.primitive

/**
 * Describes how vertex input is assembled into geometric primitives.
 *
 * [topology] determines how vertices form points, lines, or triangles. [primitiveRestartEnabled]
 * optionally allows indexed strip geometry to contain explicit breaks without requiring separate
 * draw calls.
 */
data class PrimitiveState(
    val topology: PrimitiveTopology = PrimitiveTopology.TriangleList,

    /**
     * Enables primitive restart for indexed drawing.
     *
     * When enabled, the maximum value representable by the active index format is treated as a
     * restart marker rather than as a vertex index. For example, `0xFFFF` is the restart value for
     * 16-bit indices and `0xFFFFFFFF` for 32-bit indices.
     *
     * Primitive restart is useful for storing several disconnected strips in one index buffer. For
     * example:
     *
     * ```
     * 0, 1, 2, 3, RESTART, 4, 5, 6, 7
     * ```
     *
     * can represent two independent triangle strips in one indexed draw.
     *
     * The marker is [IndexFormat.restartIndex] for the format selected by the index binding.
     * It is compared before adding the draw's `baseVertex`, so relocating a mesh does not
     * relocate its strip breaks. A marker counts toward `indexCount`, but does not select a
     * vertex. Restart has no effect on non-indexed draws and begins afresh for each instance.
     *
     * When disabled, the maximum stored value is an ordinary index. A backend that cannot
     * preserve this behavior for a requested topology must report the unsupported combination
     * rather than silently treating that vertex as a break.
     */
    val primitiveRestartEnabled: Boolean = false,
) {
    init {
        require(
            !primitiveRestartEnabled ||
                    topology == PrimitiveTopology.LineStrip ||
                    topology == PrimitiveTopology.TriangleStrip
        ) { "Primitive restart requires a strip topology" }
    }
}