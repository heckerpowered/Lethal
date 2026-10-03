/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader

import heckerpowered.render.shader.reflection.ShaderInterfaceDescription

/**
 * Backend code and original interface facts produced by one CPU compilation.
 * No GPU object is created. The module description is consumed by the existing device creation path;
 * reflection supplies mechanical layout, while application semantics remain the producer's contract.
 */
class ShaderCompilation(module: ShaderModuleDescription, val reflection: ShaderInterfaceDescription) {
    val module = module.copy(
        code = when (val code = module.code) {
            is ShaderSource -> code.copy()
            is ShaderBinary -> ShaderBinary.copyOf(code.bytes, code.format, code.label)
        }
    )
}