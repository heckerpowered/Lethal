/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.parameter

import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.shader.binding.PushConstantField
import heckerpowered.render.engine.support.collection.toUnmodifiableList

/**
 * Combines named numeric values into one shader parameter block with a declared byte layout.
 *
 * Each field selects bytes already encoded for the shader and places them at its block-relative
 * offset. Unnamed bytes are zero-filled. Packing does not calculate member alignment, convert
 * numeric types, or add a uniform-buffer binding; the shader interface selects how to consume
 * the resulting [output] parameter.
 *
 * Fields must fit within [sizeBytes] without overlap and have exactly their declared byte sizes.
 * The output name must be absent from the input so a derived block cannot replace a supplied
 * parameter silently. Layout bounds and overlap are checked at construction; input sizes and
 * output-name conflicts are checked by [derive].
 */
class NumericPacking(
    val output: ParameterName,
    val sizeBytes: Int,
    fields: List<PushConstantField>,
) {
    val fields = fields.toUnmodifiableList()

    init {
        require(sizeBytes > 0) { "Packed parameter ${output.value} requires a positive block size" }
        for ([name, offsetBytes, fieldSizeBytes] in this.fields) {
            require(fieldSizeBytes > 0) { "Packed field ${name.value} requires a positive byte size" }
            require(offsetBytes >= 0 && offsetBytes <= sizeBytes - fieldSizeBytes) { "Packed field ${name.value} at offset $offsetBytes with size $fieldSizeBytes does not fit in ${output.value}'s $sizeBytes-byte block" }
        }
        var previousEndBytes = 0
        for ([name, offsetBytes, fieldSizeBytes] in this.fields.sortedBy { it.offsetBytes }) {
            require(offsetBytes >= previousEndBytes) { "Packed field ${name.value} overlaps another field in ${output.value}" }
            previousEndBytes = offsetBytes + fieldSizeBytes
        }
    }

    fun derive(input: ParameterValues): ParameterValues {
        val block = ByteArray(sizeBytes)
        for ([name, offsetBytes, fieldSizeBytes] in fields) {
            val value = input.requireNumeric(name)
            require(value.sizeBytes == fieldSizeBytes) { "Packed field ${name.value} requires $fieldSizeBytes bytes, but its input contains ${value.sizeBytes}" }
            value.bytes().copyInto(block, offsetBytes)
        }
        require(output !in input.values) { "Parameter derivation overwrites a provider" }
        return input.replacing(output.value, NumericParameterValue(block))
    }
}
