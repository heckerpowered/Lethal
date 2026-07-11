/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import heckerpowered.bridge.FreestandingRepresentation
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.platform.loadOrNull
import kotlin.math.abs
import kotlin.math.floor

interface BlockPositionView {
    val x: Int
    val y: Int
    val z: Int

    fun isZero(): Boolean {
        return x == 0 && y == 0 && z == 0
    }

    operator fun get(index: Int): Int {
        return when (index) {
            0 -> x
            1 -> y
            2 -> z
            else -> throw IllegalArgumentException("Block position axis index must be 0, 1, or 2: $index")
        }
    }

    fun immutable(): BlockPositionView {
        return this
    }

    fun asLong(): Long {
        return BlockPositions.asLong(this)
    }

    fun asLong(format: BlockPositionPackingFormat): Long {
        return BlockPositions.asLong(this, format)
    }

    fun distanceSquaredTo(other: BlockPositionView): Long {
        val deltaX = x.toLong() - other.x.toLong()
        val deltaY = y.toLong() - other.y.toLong()
        val deltaZ = z.toLong() - other.z.toLong()

        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
    }

    fun manhattanDistanceTo(other: BlockPositionView): Int {
        return abs(x - other.x) +
                abs(y - other.y) +
                abs(z - other.z)
    }
}

interface BlockPositionProvider {
    companion object {
        val Freestanding: BlockPositionProvider = FreestandingBlockPositionProvider
        val Hosting: BlockPositionProvider?
            get() = Services.loadOrNull<BlockPositionProvider>()
        val Auto: BlockPositionProvider
            get() = Hosting ?: Freestanding
    }

    fun position(x: Int, y: Int, z: Int): BlockPositionView {
        return Freestanding.position(x, y, z)
    }

    fun containing(x: Double, y: Double, z: Double): BlockPositionView {
        return position(
            floor(x).toInt(),
            floor(y).toInt(),
            floor(z).toInt(),
        )
    }

    fun fromPackedLong(value: Long): BlockPositionView {
        return Freestanding.fromPackedLong(value)
    }

    fun asLong(position: BlockPositionView): Long {
        return Freestanding.asLong(position)
    }
}

object FreestandingBlockPositionProvider : BlockPositionProvider {
    override fun position(x: Int, y: Int, z: Int): BlockPositionView {
        return FreestandingBlockPosition(x, y, z)
    }

    override fun fromPackedLong(value: Long): BlockPositionView {
        return fromPackedLong(value, BlockPositionPackingFormats.Default)
    }

    fun fromPackedLong(value: Long, format: BlockPositionPackingFormat): BlockPositionView {
        return FreestandingBlockPosition(format.unpackX(value), format.unpackY(value), format.unpackZ(value))
    }

    override fun asLong(position: BlockPositionView): Long {
        return asLong(position, BlockPositionPackingFormats.Default)
    }

    fun asLong(position: BlockPositionView, format: BlockPositionPackingFormat): Long {
        return format.pack(position.x, position.y, position.z)
    }
}

private data class FreestandingBlockPosition(
    override val x: Int,
    override val y: Int,
    override val z: Int,
) : BlockPositionView, FreestandingRepresentation

object BlockPositions {
    var Provider: BlockPositionProvider = BlockPositionProvider.Auto

    val Zero: BlockPositionView = Provider.position(0, 0, 0)
    val One: BlockPositionView = Provider.position(1, 1, 1)
    val UnitX: BlockPositionView = Provider.position(1, 0, 0)
    val UnitY: BlockPositionView = Provider.position(0, 1, 0)
    val UnitZ: BlockPositionView = Provider.position(0, 0, 1)

    @JvmStatic
    fun of(x: Int, y: Int, z: Int): BlockPositionView {
        return Provider.position(x, y, z)
    }

    @JvmStatic
    fun containing(x: Double, y: Double, z: Double): BlockPositionView {
        return Provider.containing(x, y, z)
    }

    @JvmStatic
    fun fromPackedLong(value: Long): BlockPositionView {
        return Provider.fromPackedLong(value)
    }

    @JvmStatic
    fun fromPackedLong(value: Long, format: BlockPositionPackingFormat): BlockPositionView {
        return FreestandingBlockPositionProvider.fromPackedLong(value, format)
    }

    @JvmStatic
    fun asLong(position: BlockPositionView): Long {
        return Provider.asLong(position)
    }

    @JvmStatic
    fun asLong(position: BlockPositionView, format: BlockPositionPackingFormat): Long {
        return FreestandingBlockPositionProvider.asLong(position, format)
    }

    @JvmStatic
    fun min(first: BlockPositionView, second: BlockPositionView): BlockPositionView {
        return first.componentMin(second)
    }

    @JvmStatic
    fun max(first: BlockPositionView, second: BlockPositionView): BlockPositionView {
        return first.componentMax(second)
    }

    @JvmStatic
    fun distanceSquared(first: BlockPositionView, second: BlockPositionView): Long {
        return first.distanceSquaredTo(second)
    }

    @JvmStatic
    fun manhattanDistance(first: BlockPositionView, second: BlockPositionView): Int {
        return first.manhattanDistanceTo(second)
    }

    @JvmStatic
    fun cross(first: BlockPositionView, second: BlockPositionView): BlockPositionView {
        return first.cross(second)
    }

    @JvmStatic
    fun betweenClosed(first: BlockPositionView, second: BlockPositionView): Sequence<BlockPositionView> {
        return betweenClosed(first.x, first.y, first.z, second.x, second.y, second.z)
    }

    @JvmStatic
    fun betweenClosed(firstX: Int, firstY: Int, firstZ: Int, secondX: Int, secondY: Int, secondZ: Int): Sequence<BlockPositionView> {
        val minimumX = minOf(firstX, secondX)
        val minimumY = minOf(firstY, secondY)
        val minimumZ = minOf(firstZ, secondZ)

        val maximumX = maxOf(firstX, secondX)
        val maximumY = maxOf(firstY, secondY)
        val maximumZ = maxOf(firstZ, secondZ)

        return sequence {
            for (z in minimumZ..maximumZ) {
                for (y in minimumY..maximumY) {
                    for (x in minimumX..maximumX) {
                        yield(of(x, y, z))
                    }
                }
            }
        }
    }

    @JvmStatic
    fun firstBetweenClosed(first: BlockPositionView, second: BlockPositionView, predicate: (BlockPositionView) -> Boolean): BlockPositionView? {
        return betweenClosed(first, second).firstOrNull(predicate)
    }

    @JvmStatic
    fun withinManhattan(origin: BlockPositionView, reachX: Int, reachY: Int, reachZ: Int): Sequence<BlockPositionView> {
        val maximumDepth = reachX + reachY + reachZ

        return sequence {
            for (depth in 0..maximumDepth) {
                val maximumX = minOf(reachX, depth)

                for (deltaX in -maximumX..maximumX) {
                    val maximumY = minOf(reachY, depth - abs(deltaX))

                    for (deltaY in -maximumY..maximumY) {
                        val deltaZ = depth - abs(deltaX) - abs(deltaY)

                        if (deltaZ > reachZ) {
                            continue
                        }

                        yield(of(origin.x + deltaX, origin.y + deltaY, origin.z + deltaZ))

                        if (deltaZ != 0) {
                            yield(of(origin.x + deltaX, origin.y + deltaY, origin.z - deltaZ))
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun withinManhattan(originX: Int, originY: Int, originZ: Int, reachX: Int, reachY: Int, reachZ: Int): Sequence<BlockPositionView> {
        return withinManhattan(of(originX, originY, originZ), reachX, reachY, reachZ)
    }
}

fun BlockPositionView.pack(): Long {
    return asLong()
}

fun BlockPositionView.pack(format: BlockPositionPackingFormat): Long {
    return asLong(format)
}

fun BlockPositionView.offset(offsetX: Int, offsetY: Int, offsetZ: Int): BlockPositionView {
    if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
        return this
    }

    return BlockPositions.of(x + offsetX, y + offsetY, z + offsetZ)
}

fun BlockPositionView.offset(direction: BlockDirection, steps: Int = 1): BlockPositionView {
    if (steps == 0) {
        return this
    }

    return offset(
        direction.stepX * steps,
        direction.stepY * steps,
        direction.stepZ * steps,
    )
}

fun BlockPositionView.above(steps: Int = 1): BlockPositionView {
    return offset(BlockDirection.Up, steps)
}

fun BlockPositionView.below(steps: Int = 1): BlockPositionView {
    return offset(BlockDirection.Down, steps)
}

fun BlockPositionView.north(steps: Int = 1): BlockPositionView {
    return offset(BlockDirection.North, steps)
}

fun BlockPositionView.south(steps: Int = 1): BlockPositionView {
    return offset(BlockDirection.South, steps)
}

fun BlockPositionView.west(steps: Int = 1): BlockPositionView {
    return offset(BlockDirection.West, steps)
}

fun BlockPositionView.east(steps: Int = 1): BlockPositionView {
    return offset(BlockDirection.East, steps)
}

fun BlockPositionView.withX(x: Int): BlockPositionView {
    return BlockPositions.of(x, y, z)
}

fun BlockPositionView.withY(y: Int): BlockPositionView {
    return BlockPositions.of(x, y, z)
}

fun BlockPositionView.withZ(z: Int): BlockPositionView {
    return BlockPositions.of(x, y, z)
}

fun BlockPositionView.rotate(rotation: BlockRotation): BlockPositionView {
    return when (rotation) {
        BlockRotation.None -> this
        BlockRotation.Clockwise90 -> BlockPositions.of(-z, y, x)
        BlockRotation.Clockwise180 -> BlockPositions.of(-x, y, -z)
        BlockRotation.Counterclockwise90 -> BlockPositions.of(z, y, -x)
    }
}

fun BlockPositionView.cross(other: BlockPositionView): BlockPositionView {
    return BlockPositions.of(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )
}

fun BlockPositionView.componentMin(other: BlockPositionView): BlockPositionView {
    return BlockPositions.of(
        minOf(x, other.x),
        minOf(y, other.y),
        minOf(z, other.z),
    )
}

fun BlockPositionView.componentMax(other: BlockPositionView): BlockPositionView {
    return BlockPositions.of(
        maxOf(x, other.x),
        maxOf(y, other.y),
        maxOf(z, other.z),
    )
}

operator fun BlockPositionView.plus(other: BlockPositionView): BlockPositionView {
    return BlockPositions.of(
        x + other.x,
        y + other.y,
        z + other.z,
    )
}

operator fun BlockPositionView.minus(other: BlockPositionView): BlockPositionView {
    return BlockPositions.of(
        x - other.x,
        y - other.y,
        z - other.z,
    )
}

operator fun BlockPositionView.unaryMinus(): BlockPositionView {
    return BlockPositions.of(-x, -y, -z)
}

operator fun BlockPositionView.times(scale: Int): BlockPositionView {
    return when (scale) {
        0 -> BlockPositions.Zero
        1 -> this
        else -> BlockPositions.of(
            x * scale,
            y * scale,
            z * scale,
        )
    }
}

enum class BlockDirection(
    val stepX: Int,
    val stepY: Int,
    val stepZ: Int,
) {
    Down(0, -1, 0),
    Up(0, 1, 0),
    North(0, 0, -1),
    South(0, 0, 1),
    West(-1, 0, 0),
    East(1, 0, 0);

    val opposite: BlockDirection
        get() = when (this) {
            Down -> Up
            Up -> Down
            North -> South
            South -> North
            West -> East
            East -> West
        }
}

enum class BlockRotation {
    None,
    Clockwise90,
    Clockwise180,
    Counterclockwise90,
}

interface BlockPositionPackingFormat {
    fun pack(x: Int, y: Int, z: Int): Long
    fun unpackX(value: Long): Int
    fun unpackY(value: Long): Int
    fun unpackZ(value: Long): Int
}

object BlockPositionPackingFormats {
    var Default: BlockPositionPackingFormat = XZY

    object Auto : BlockPositionPackingFormat {
        override fun pack(x: Int, y: Int, z: Int): Long {
            val provider = BlockPositions.Provider
            return provider.asLong(provider.position(x, y, z))
        }

        override fun unpackX(value: Long): Int {
            return BlockPositions.fromPackedLong(value).x
        }

        override fun unpackY(value: Long): Int {
            return BlockPositions.fromPackedLong(value).y
        }

        override fun unpackZ(value: Long): Int {
            return BlockPositions.fromPackedLong(value).z
        }
    }

    object XYZ : BlockPositionPackingFormat {
        private const val X_OFFSET: Int = 38
        private const val Y_OFFSET: Int = 26
        private const val Z_OFFSET: Int = 0

        override fun pack(x: Int, y: Int, z: Int): Long {
            return BlockPositionPacking.packWithOffsets(x, y, z, X_OFFSET, Y_OFFSET, Z_OFFSET)
        }

        override fun unpackX(value: Long): Int {
            return BlockPositionPacking.unpackSigned(value, X_OFFSET, BlockPositionPacking.HORIZONTAL_BITS)
        }

        override fun unpackY(value: Long): Int {
            return BlockPositionPacking.unpackSigned(value, Y_OFFSET, BlockPositionPacking.VERTICAL_BITS)
        }

        override fun unpackZ(value: Long): Int {
            return BlockPositionPacking.unpackSigned(value, Z_OFFSET, BlockPositionPacking.HORIZONTAL_BITS)
        }
    }

    object XZY : BlockPositionPackingFormat {
        private const val X_OFFSET: Int = 38
        private const val Y_OFFSET: Int = 0
        private const val Z_OFFSET: Int = 12

        override fun pack(x: Int, y: Int, z: Int): Long {
            return BlockPositionPacking.packWithOffsets(x, y, z, X_OFFSET, Y_OFFSET, Z_OFFSET)
        }

        override fun unpackX(value: Long): Int {
            return BlockPositionPacking.unpackSigned(value, X_OFFSET, BlockPositionPacking.HORIZONTAL_BITS)
        }

        override fun unpackY(value: Long): Int {
            return BlockPositionPacking.unpackSigned(value, Y_OFFSET, BlockPositionPacking.VERTICAL_BITS)
        }

        override fun unpackZ(value: Long): Int {
            return BlockPositionPacking.unpackSigned(value, Z_OFFSET, BlockPositionPacking.HORIZONTAL_BITS)
        }
    }
}

object BlockPositionPacking {
    const val HORIZONTAL_BITS: Int = 26
    const val VERTICAL_BITS: Int = 12

    const val HORIZONTAL_MASK: Long = (1L shl HORIZONTAL_BITS) - 1L
    const val VERTICAL_MASK: Long = (1L shl VERTICAL_BITS) - 1L

    @JvmStatic
    fun pack(x: Int, y: Int, z: Int, format: BlockPositionPackingFormat = BlockPositionPackingFormats.Auto): Long {
        return format.pack(x, y, z)
    }

    @JvmStatic
    fun unpackX(value: Long, format: BlockPositionPackingFormat = BlockPositionPackingFormats.Auto): Int {
        return format.unpackX(value)
    }

    @JvmStatic
    fun unpackY(value: Long, format: BlockPositionPackingFormat = BlockPositionPackingFormats.Auto): Int {
        return format.unpackY(value)
    }

    @JvmStatic
    fun unpackZ(value: Long, format: BlockPositionPackingFormat = BlockPositionPackingFormats.Auto): Int {
        return format.unpackZ(value)
    }

    fun packWithOffsets(x: Int, y: Int, z: Int, xOffset: Int, yOffset: Int, zOffset: Int): Long {
        var value = 0L
        value = value or ((x.toLong() and HORIZONTAL_MASK) shl xOffset)
        value = value or ((y.toLong() and VERTICAL_MASK) shl yOffset)
        value = value or ((z.toLong() and HORIZONTAL_MASK) shl zOffset)
        return value
    }

    fun unpackSigned(value: Long, offset: Int, bits: Int): Int {
        val leftShift = 64 - offset - bits
        val rightShift = 64 - bits
        return (value shl leftShift shr rightShift).toInt()
    }
}
