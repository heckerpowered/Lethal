/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

import java.lang.Long.compareUnsigned
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.Buffer
import java.nio.ByteBuffer

/**
 * Copies a nonempty native region into independent host bytes before returning.
 *
 * Rejects a nonpositive size, a null address, an address outside the host pointer width, or an
 * unrepresentable exclusive end of `[source, source + sizeBytes)`. On 32-bit hosts, zero-extended
 * and correctly sign-extended addresses are accepted and normalized before copying; 64-bit
 * addresses are treated as unsigned bit patterns. Other pointer widths are unsupported.
 *
 * These numeric checks cannot establish allocation, readability, or lifetime. The caller must supply at least
 * [sizeBytes] readable bytes at [source], keep their allocation alive throughout the call, and
 * prevent concurrent modification. Invalid storage can terminate the process during the copy.
 * No byte-order conversion or push-constant layout validation is performed.
 */
fun snapshotNativeBytes(source: NativeAddress, sizeBytes: Int): ByteArray {
    val address = validateNativeByteRange(source, sizeBytes, UnsafeNativeMemory.pointerBytes)
    val snapshot = ByteArray(sizeBytes)
    UnsafeNativeMemory.copyInto(address, snapshot)
    return snapshot
}

internal fun validateNativeByteRange(source: NativeAddress, sizeBytes: Int, pointerBytes: Int): NativeAddress {
    require(sizeBytes > 0) { "Native snapshot size must be positive" }
    require(source.rawValue != 0L) { "Native snapshot source must not be null" }

    return when (pointerBytes) {
        4 -> validate32BitByteRange(source, sizeBytes)
        8 -> validate64BitByteRange(source, sizeBytes)
        else -> error("Unsupported native pointer width: $pointerBytes bytes")
    }
}

private fun validate32BitByteRange(source: NativeAddress, sizeBytes: Int): NativeAddress {
    val zeroExtendedAddress = source.rawValue and 0xFFFF_FFFFL
    require(source.rawValue == zeroExtendedAddress || source.rawValue == zeroExtendedAddress.toInt().toLong()) { "Native snapshot source must fit a 32-bit pointer" }

    val exclusiveEnd = zeroExtendedAddress + sizeBytes.toLong()
    require(exclusiveEnd <= 0xFFFF_FFFFL) { "Native snapshot exclusive end must fit a 32-bit pointer" }

    return NativeAddress(zeroExtendedAddress)
}

private fun validate64BitByteRange(source: NativeAddress, sizeBytes: Int): NativeAddress {
    val exclusiveEnd = source.rawValue + sizeBytes.toLong()
    require(compareUnsigned(exclusiveEnd, source.rawValue) > 0) { "Native snapshot exclusive end must not wrap" }
    return source
}

internal object UnsafeNativeMemory {
    private val access by lazy { UnsafeAccess() }

    val pointerBytes: Int
        get() = access.pointerBytes

    fun address(buffer: ByteBuffer): Long = access.address(buffer)

    fun copyInto(source: NativeAddress, destination: ByteArray) = access.copyInto(source, destination)

    private class UnsafeAccess {
        private val unsafeClass = Class.forName("sun.misc.Unsafe")
        private val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)

        val pointerBytes by lazy {
            (invokeMemoryMethod(unsafeClass.getMethod("addressSize"), unsafe) as Number).toInt()
        }

        fun address(buffer: ByteBuffer): Long =
            invokeMemoryMethod(readLongMethod, unsafe, buffer, bufferAddressOffset) as Long

        fun copyInto(source: NativeAddress, destination: ByteArray) {
            invokeMemoryMethod(copyMemoryMethod, unsafe, null, source.rawValue, destination, byteArrayOffset, destination.size.toLong())
        }

        private val bufferAddressOffset by lazy {
            val objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field::class.java)
            val addressField = Buffer::class.java.getDeclaredField("address")
            invokeMemoryMethod(objectFieldOffset, unsafe, addressField) as Long
        }
        private val readLongMethod by lazy {
            unsafeClass.getMethod("getLong", Any::class.java, Long::class.javaPrimitiveType)
        }
        private val byteArrayOffset by lazy {
            val arrayBaseOffset = unsafeClass.getMethod("arrayBaseOffset", Class::class.java)
            (invokeMemoryMethod(arrayBaseOffset, unsafe, ByteArray::class.java) as Number).toLong()
        }
        private val copyMemoryMethod by lazy {
            unsafeClass.getMethod("copyMemory", Any::class.java, Long::class.javaPrimitiveType, Any::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
        }
    }
}

internal fun invokeMemoryMethod(method: Method, receiver: Any?, vararg arguments: Any?): Any? = try {
    method.invoke(receiver, *arguments)
} catch (failure: InvocationTargetException) {
    throw failure.targetException
}
