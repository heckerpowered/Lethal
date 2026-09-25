/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.buffer

/**
 * Declares a way in which graphics or compute operations may use a buffer's storage.
 *
 * Usage is chosen when the buffer is created. Several roles can be combined so the same bytes
 * can be produced by one operation and consumed by another. For example, combining [Storage]
 * and [Indirect] allows a compute shader to prepare parameters for a later indirect draw.
 *
 * These roles describe permitted operations, not the buffer's current binding or access state.
 * Ordering between accesses must be established separately. CPU mapping and memory placement
 * are separate concerns rather than implications of a particular usage.
 *
 * A device must reject unsupported roles or combinations rather than silently remove them.
 */
enum class BufferUsage {
    /**
     * Allows vertex input to fetch attributes for vertices or instances from the buffer.
     *
     * A vertex-input layout describes how each element's bytes become shader inputs, including
     * component formats, attribute offsets, and the stride between elements. Stepping behavior
     * determines whether the next element follows the vertex index or the instance index.
     *
     * Typical uses include mesh positions and texture coordinates, or transforms shared by
     * all vertices of an instance.
     */
    Vertex,

    /**
     * Allows indexed draws to read vertex indices from the buffer.
     *
     * Indices select vertices without duplicating their attribute data. For example, the index
     * sequence `0, 1, 2, 2, 3, 0` can draw two triangles from four shared vertices in a triangle
     * list.
     *
     * The index binding selects the integer representation. This role permits index reads;
     * any buffer supplying the selected vertex attributes also needs [Vertex].
     */
    Index,

    /**
     * Allows shaders to read a uniform or constant-buffer block from the buffer.
     *
     * Uniform blocks group parameters such as camera matrices, lighting settings, or material
     * values. Shader declarations and their data-layout rules determine where each field is
     * read within the bound range.
     *
     * This role permits shader reads, not shader writes. The word "uniform" describes the
     * shader interface rather than requiring the allocation's contents to remain unchanged.
     */
    Uniform,

    /**
     * Allows shaders to access data through storage-buffer bindings.
     *
     * Storage buffers can hold arrays and structures that shaders process or generate, such as
     * particle states, visible-object lists, or the arguments for an indirect draw. Their layout
     * is defined by the shader's buffer declaration.
     *
     * A binding may expose read-only or writable access. Writes and atomics remain subject to
     * the binding's access mode, shader declarations, and device support; declaring this usage
     * does not make every access writable.
     */
    Storage,

    /**
     * Allows shaders to fetch formatted texels from a buffer range by integer index.
     *
     * A formatted texel-buffer view supplies the component format used to interpret the bytes.
     * This is useful for a table of equally formatted values accessed as texels, rather than as
     * the named fields of a uniform block.
     *
     * Shader access through this role is read-only. Fetches select texels directly without a
     * sampler's interpolation or mip-level selection. A plain [GpuBufferView] selects bytes
     * but does not supply the required texel format.
     */
    UniformTexel,

    /**
     * Allows shaders to access formatted texels through storage-texel-buffer views.
     *
     * Like [UniformTexel], this exposes a buffer range as integer-indexed texels with a format
     * supplied by the view. It additionally permits writable shader access, for example when
     * computing a table of formatted values for later use.
     *
     * Reads, writes, and atomics require compatible binding access, shader declarations, and
     * format and device support. Access is not filtered sampling.
     */
    StorageTexel,

    /**
     * Allows the GPU to read draw or dispatch arguments from the buffer.
     *
     * The encoded command selects the operation and argument layout; the buffer supplies values
     * such as vertex counts, instance counts, or workgroup counts. Commands that support an
     * indirect command count may also read that count from a buffer with this usage.
     *
     * For example, a compute shader can determine which objects are visible and write arguments
     * that a later draw consumes without first returning those arguments to the CPU. That buffer
     * also needs [Storage] for the shader writes, with ordering established before the draw.
     * [Indirect] alone permits reading the parameters, not generating them.
     */
    Indirect,

    /**
     * Allows transfer commands to read the buffer as a copy source.
     *
     * A staging buffer can hold uploaded mesh or image data that is then copied into another
     * buffer or a texture. This role permits the transfer to read those source bytes.
     *
     * It does not provide CPU read access; mapping or readback requires a separate mechanism.
     */
    TransferSource,

    /**
     * Allows transfer commands to write the buffer through copies, uploads, or fills.
     *
     * For example, vertex data uploaded by the application needs this role for the upload and
     * [Vertex] for the later vertex fetch. A copy destination must have sufficient capacity for
     * the selected range.
     *
     * Shader writes use [Storage] or [StorageTexel]. Query-result writes use [QueryResolve],
     * which is a separate permission in this interface.
     */
    TransferDestination,

    /**
     * Allows query commands to write query results into the buffer.
     *
     * Queries measure execution properties such as timestamps or the number of samples that
     * pass an occlusion test. Resolving writes their results into ordinary buffer storage so
     * another operation can consume them.
     *
     * The query API determines the result layout and when results are available. Reading them
     * on the CPU is a separate step. Other uses of the result buffer require their own roles,
     * such as [TransferSource] for a later copy or [Storage] for shader access.
     */
    QueryResolve,
}