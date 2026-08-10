/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

/**
 * OpenGL entry points available for the context current on the calling thread.
 *
 * Implementations must verify all mandatory inherited functions before exposing
 * an instance. Optional function groups are exposed only when every operation in
 * that group is available.
 *
 * Equivalent core and extension entry points are normalized by the adapter and
 * are never selected by shared backend code.
 *
 * This type does not own the OpenGL context. The context used to create an
 * instance must remain current whenever its functions are invoked.
 */
interface OpenGLFunctions :
    OpenGLShaderFunctions,
    OpenGLBufferFunctions,
    OpenGLTextureFunctions,
    OpenGLVertexInputFunctions,
    OpenGLDrawingFunctions,
    OpenGLStateFunctions {

    /**
     * Framebuffer and renderbuffer operations, or `null` when the device reports
     * render-target creation as unsupported and renders only to output targets
     * managed by the host.
     */
    val framebuffers: OpenGLFramebufferFunctions?

    /**
     * Vertex-array-object operations, or `null` when the backend must restore
     * vertex input state directly before drawing.
     */
    val vertexArrays: OpenGLVertexArrayFunctions?

    /**
     * Uniform-buffer operations, or `null` when uniform blocks must be uploaded
     * through individual program uniforms.
     */
    val uniformBuffers: OpenGLUniformBufferFunctions?

    /**
     * Sampler-object operations, or `null` when sampling state must be stored on
     * texture objects.
     */
    val samplers: OpenGLSamplerFunctions?

    /**
     * Shader-output color-clamp control, or `null` when both stage controls are
     * not available.
     *
     * The shared backend consumes the vertex and fragment controls together.
     * Absence of this group does not imply that floating-point color attachments
     * are unsupported.
     */
    val shaderColorClamping: OpenGLShaderColorClampingFunctions?

    /**
     * SPIR-V shader loading and specialization, or `null` when the current
     * adapter and context cannot consume SPIR-V shader modules.
     */
    val spirVShaders: OpenGLSpirVShaderFunctions?
}
