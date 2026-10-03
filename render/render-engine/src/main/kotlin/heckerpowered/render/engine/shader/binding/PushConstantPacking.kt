/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.draw.PreparedPushConstantWrite
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.shader.parameter.NumericPacking
import heckerpowered.render.shader.ShaderStage

/**
 * Builds one push-constant write from named numeric parameters.
 *
 * [NumericPacking] derives a named numeric parameter block. This packing instead produces
 * a push-constant write with shader stages and a destination byte offset.
 *
 * Fields use offsets within this block; [offsetBytes] places the completed block in the pipeline's
 * push-constant address space. Fields must fit without overlap, and every field value must have
 * its declared byte size. Unnamed bytes are zero-filled so padding cannot retain another draw's
 * values. The declaration must match the shader layout; member alignment is not inferred here.
 *
 * The block's size and destination offset must be four-byte aligned. Resolution snapshots its
 * bytes into a prepared write. Pipeline-range coverage and shader-stage visibility are checked
 * when that write is encoded against the selected pipeline.
 */
class PushConstantPacking(
    val stages: Set<ShaderStage>,
    val offsetBytes: Int,
    val sizeBytes: Int,
    fields: List<PushConstantField>,
) {
    val fields = fields.toList()

    init {
        require(sizeBytes > 0 && sizeBytes % 4 == 0 && offsetBytes >= 0 && offsetBytes % 4 == 0)
        val coverage = BooleanArray(sizeBytes)
        for ([_, offsetBytes, fieldSizeBytes] in this.fields) {
            require(offsetBytes >= 0 && fieldSizeBytes > 0 && offsetBytes <= sizeBytes - fieldSizeBytes)

            for (byte in offsetBytes until offsetBytes + fieldSizeBytes) {
                require(!coverage[byte]) { "Overlapping push fields" }
                coverage[byte] = true
            }
        }
    }

    internal fun resolve(values: ParameterValues): PreparedPushConstantWrite {
        val bytes = ByteArray(sizeBytes)
        for ([name, fieldOffsetBytes, fieldSizeBytes] in fields) {
            val value = values.requireNumeric(name)
            require(value.sizeBytes == fieldSizeBytes)
            value.bytes().copyInto(bytes, fieldOffsetBytes)
        }
        return PreparedPushConstantWrite(stages, bytes, offsetBytes)
    }
}
