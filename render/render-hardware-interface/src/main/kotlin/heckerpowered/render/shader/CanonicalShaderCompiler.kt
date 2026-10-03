/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader

/**
 * Actual canonical-source compiler supplied by backend assembly, independently of the GL binding.
 * Device assembly must provide a matching compiler. The device is responsible for closing it;
 * callers must not close it. Destruction releases only compiler resources and follows
 * the RHI fatal-cleanup contract; it does not wait for GPU work or destroy the host graphics context.
 */
interface CanonicalShaderCompiler : AutoCloseable {
    /**
     * Compiles canonical Vulkan GLSL source and its original reflected interface before GPU creation.
     * The initial implementation supports vertex/fragment main and rejects unsupported shapes.
     * Origin is a virtual source name, separate from the diagnostic label. Quoted includes are relative
     * to their requesting source; angle includes are relative to the root of the supplied snapshot.
     * Root text and includes must belong to one stable generation. Implementations copy retained inputs.
     *
     * Canonical two-dimensional normalized sampling coordinates follow logical texture coordinates,
     * with (0, 0) at the top-left and y increasing downwards. For a W × H extent, texel (x, y) has its
     * center at ((x + 0.5) / W, (y + 0.5) / H). Backends lower supported sampling operations to their
     * native coordinate convention; this contract does not imply support for every sampling opcode.
     *
     * This operation does not accept final native OpenGL GLSL as an interchangeable dialect.
     */
    fun compile(description: ShaderModuleDescription, origin: String, includes: Map<String, String>): ShaderCompilation
}
