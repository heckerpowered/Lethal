/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing

/** IEEE binary16 conversion for the JDK 8 baseline; round-to-nearest, ties-to-even. */
internal object HalfPrecision {
    fun decode(bits: Int): Float {
        val sign = (bits and 32768) shl 16
        var exponent = (bits ushr 10) and 31
        var fraction = bits and 1023
        if (exponent == 0) {
            if (fraction == 0) return Float.fromBits(sign)
            exponent = 1
            while ((fraction and 1024) == 0) {
                fraction = fraction shl 1; exponent--
            }
            fraction = fraction and 1023
        } else if (exponent == 31) return Float.fromBits(sign or 0x7f800000 or (fraction shl 13))
        return Float.fromBits(sign or ((exponent + 112) shl 23) or (fraction shl 13))
    }

    fun round(value: Double): Float {
        require(value.isFinite() && value >= 0.0 && value <= 65504.0) { "Scatter sample exceeds finite binary16 storage" }

        val bits = value.toFloat().toBits()
        val exponent = ((bits ushr 23) and 255) - 112
        val fraction = bits and 0x7fffff
        if (exponent < -10) return 0F
        if (exponent <= 0) {
            val mantissa = fraction or 0x800000
            val shift = 14 - exponent
            var result = mantissa ushr shift
            val remainder = mantissa and ((1 shl shift) - 1)
            val midpoint = 1 shl (shift - 1)
            if (remainder > midpoint || remainder == midpoint && (result and 1) != 0) result++
            return decode(result)
        }
        val rounded = fraction + 4095 + ((fraction ushr 13) and 1)
        return decode((exponent shl 10) + (rounded ushr 13))
    }
}
