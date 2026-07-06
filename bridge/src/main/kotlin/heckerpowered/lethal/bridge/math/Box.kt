/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.math

interface BoxView {
    val min: VectorView
    val max: VectorView

    val center: VectorView
        get() = (min + max) * 0.5
    val size: VectorView
        get() = max - min
    val extent: VectorView
        get() = size * 0.5
    val volume: Double
        get() = size.x * size.y * size.z
}

data class BoxIntersection(
    val ray: RayView,

    val enterTime: Double?,
    val exitTime: Double,

    val enterNormal: VectorView?,
    val exitNormal: VectorView,
) {
    val startsInside: Boolean
        get() = enterTime == null

    val enterPoint: VectorView?
        get() = enterTime?.let(ray::pointAt)

    val exitPoint: VectorView
        get() = ray.pointAt(exitTime)

    val nearestHitTime: Double
        get() = enterTime ?: exitTime

    val nearestHitPoint: VectorView
        get() = ray.pointAt(nearestHitTime)

    val nearestHitNormal: VectorView
        get() = enterNormal ?: exitNormal
}

fun BoxView.expandedBy(amount: Double): BoxView {
    val vector = Geometry.vector(amount, amount, amount)
    return Geometry.box(min - vector, max + vector)
}

fun BoxView.expandedBy(amount: VectorView): BoxView {
    return Geometry.box(min - amount, max + amount)
}

fun BoxView.expandedBy(negative: VectorView, positive: VectorView): BoxView {
    return Geometry.box(min - negative, max + positive)
}

fun BoxView.translatedBy(offset: VectorView): BoxView {
    return Geometry.box(min + offset, max + offset)
}

fun BoxView.movedTo(destination: VectorView): BoxView {
    return translatedBy(destination - center)
}

fun BoxView.union(point: VectorView): BoxView {
    return Geometry.box(
        Vectors.min(min, point),
        Vectors.max(max, point)
    )
}

fun BoxView.union(other: BoxView): BoxView {
    return Geometry.box(
        Vectors.min(min, other.min),
        Vectors.max(max, other.max)
    )
}

fun BoxView.closestPointTo(point: VectorView): VectorView {
    return Geometry.vector(
        point.x.coerceIn(min.x, max.x),
        point.y.coerceIn(min.y, max.y),
        point.z.coerceIn(min.z, max.z)
    )
}

fun BoxView.distanceSquaredTo(point: VectorView): Double {
    var distanceSquared = 0.0

    if (point.x < min.x) distanceSquared += (point.x - min.x).square()
    else if (point.x > max.x) distanceSquared += (point.x - max.x).square()

    if (point.y < min.y) distanceSquared += (point.y - min.y).square()
    else if (point.y > max.y) distanceSquared += (point.y - max.y).square()

    if (point.z < min.z) distanceSquared += (point.z - min.z).square()
    else if (point.z > max.z) distanceSquared += (point.z - max.z).square()

    return distanceSquared
}

fun BoxView.contains(point: VectorView): Boolean {
    return point.x > min.x && point.x < max.x &&
            point.y > min.y && point.y < max.y &&
            point.z > min.z && point.z < max.z
}

fun BoxView.containsOrOn(point: VectorView): Boolean {
    return point.x in min.x..max.x &&
            point.y in min.y..max.y &&
            point.z in min.z..max.z
}

fun BoxView.contains(other: BoxView): Boolean {
    return contains(other.min) && contains(other.max)
}

fun BoxView.containsOrOn(other: BoxView): Boolean {
    return containsOrOn(other.min) && containsOrOn(other.max)
}

fun BoxView.containsHorizontal(point: VectorView): Boolean {
    return point.x > min.x && point.x < max.x &&
            point.z > min.z && point.z < max.z
}

fun BoxView.containsOrOnHorizontal(point: VectorView): Boolean {
    return point.x in min.x..max.x &&
            point.z in min.z..max.z
}

fun BoxView.containsHorizontal(other: BoxView): Boolean {
    return containsHorizontal(other.min) && containsHorizontal(other.max)
}

fun BoxView.containsOrOnHorizontal(other: BoxView): Boolean {
    return containsOrOnHorizontal(other.min) && containsOrOnHorizontal(other.max)
}

fun BoxView.intersects(other: BoxView): Boolean {
    return min.x <= other.max.x && other.min.x <= max.x &&
            min.y <= other.max.y && other.min.y <= max.y &&
            min.z <= other.max.z && other.min.z <= max.z
}

fun BoxView.intersectsHorizontal(other: BoxView): Boolean {
    return min.x <= other.max.x && other.min.x <= max.x &&
            min.z <= other.max.z && other.min.z <= max.z
}

fun BoxView.overlap(other: BoxView): BoxView {
    if (!intersects(other)) {
        return Geometry.box(Vectors.Zero, Vectors.Zero)
    }

    return Geometry.box(
        Vectors.max(min, other.min),
        Vectors.min(max, other.max)
    )
}

fun BoxView.vertices(): Array<VectorView> {
    return arrayOf(
        Geometry.vector(min.x, min.y, min.z),
        Geometry.vector(min.x, min.y, max.z),
        Geometry.vector(min.x, max.y, min.z),
        Geometry.vector(min.x, max.y, max.z),
        Geometry.vector(max.x, min.y, min.z),
        Geometry.vector(max.x, min.y, max.z),
        Geometry.vector(max.x, max.y, min.z),
        Geometry.vector(max.x, max.y, max.z)
    )
}

fun BoxView.intersectsSphere(center: VectorView, radius: Double): Boolean {
    return intersectsSphereSquared(center, radius * radius)
}

fun BoxView.intersectsSphereSquared(center: VectorView, radiusSquared: Double): Boolean {
    return distanceSquaredTo(center) <= radiusSquared
}

fun BoxView.pointBoxIntersection(point: VectorView): Boolean {
    return containsOrOn(point)
}

fun BoxView.intersect(ray: RayView, length: Double? = null): BoxIntersection? {
    val directionLength = ray.direction.length
    if (directionLength.isNearlyZero()) return null

    val maxTime = length?.let { it / directionLength }

    var enterTime = Double.NEGATIVE_INFINITY
    var exitTime = Double.POSITIVE_INFINITY

    var enterNormal: VectorView? = null
    var exitNormal: VectorView? = null

    fun testAxis(origin: Double, direction: Double, minimum: Double, maximum: Double, minimumNormal: VectorView, maximumNormal: VectorView): Boolean {
        if (direction.isNearlyZero()) return origin in minimum..maximum

        val inverseDirection = 1.0 / direction

        var axisEnterTime = (minimum - origin) * inverseDirection
        var axisExitTime = (maximum - origin) * inverseDirection
        var axisEnterNormal = minimumNormal
        var axisExitNormal = maximumNormal

        if (axisEnterTime > axisExitTime) {
            axisEnterTime = axisExitTime.also { axisExitTime = axisEnterTime }
            axisEnterNormal = axisExitNormal.also { axisExitNormal = axisEnterNormal }
        }

        if (axisEnterTime > enterTime) {
            enterTime = axisEnterTime
            enterNormal = axisEnterNormal
        }

        if (axisExitTime < exitTime) {
            exitTime = axisExitTime
            exitNormal = axisExitNormal
        }

        return enterTime <= exitTime
    }

    if (!testAxis(ray.origin.x, ray.direction.x, min.x, max.x, -Vectors.UnitX, Vectors.UnitX)) return null
    if (!testAxis(ray.origin.y, ray.direction.y, min.y, max.y, -Vectors.UnitY, Vectors.UnitY)) return null
    if (!testAxis(ray.origin.z, ray.direction.z, min.z, max.z, -Vectors.UnitZ, Vectors.UnitZ)) return null

    if (exitTime < 0.0) return null
    if (maxTime != null && enterTime > maxTime) return null

    val startsInside = enterTime < 0.0

    return BoxIntersection(
        ray = ray,
        enterTime = if (startsInside) null else enterTime,
        exitTime = if (maxTime != null) exitTime.coerceAtMost(maxTime) else exitTime,
        enterNormal = if (startsInside) null else enterNormal,
        exitNormal = exitNormal ?: Vectors.Zero
    )
}