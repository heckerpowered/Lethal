/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.math

interface BoxView {
    val minX: Double
    val minY: Double
    val minZ: Double

    val maxX: Double
    val maxY: Double
    val maxZ: Double

    val min: VectorView
        get() = Geometry.vector(minX, minY, minZ)
    val max: VectorView
        get() = Geometry.vector(maxX, maxY, maxZ)

    val center: VectorView
        get() = Geometry.vector((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5)
    val size: VectorView
        get() = Geometry.vector(maxX - minX, maxY - minY, maxZ - minZ)
    val extent: VectorView
        get() = Geometry.vector((maxX - minX) * 0.5, (maxY - minY) * 0.5, (maxZ - minZ) * 0.5)
    val volume: Double
        get() = (maxX - minX) * (maxY - minY) * (maxZ - minZ)
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

    val enterSurfacePoint: VectorView?
        get() = enterTime?.let(ray::pointAt)

    val enterPoint: VectorView
        get() = enterTime?.let(ray::pointAt) ?: ray.origin

    val exitPoint: VectorView
        get() = ray.pointAt(exitTime)

    val nearestSurfaceHitTime: Double
        get() = enterTime ?: exitTime

    val nearestHitTime: Double
        get() = enterTime ?: 0.0

    val nearestHitPoint: VectorView
        get() = enterTime?.let(ray::pointAt) ?: ray.origin

    val nearestSurfaceHitPoint: VectorView
        get() = ray.pointAt(nearestSurfaceHitTime)

    val nearestHitNormal: VectorView
        get() = enterNormal ?: exitNormal
}

fun BoxView.expandedBy(amount: Double): BoxView {
    return Geometry.box(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount)
}

fun BoxView.expandedBy(amount: VectorView): BoxView {
    return Geometry.box(minX - amount.x, minY - amount.y, minZ - amount.z, maxX + amount.x, maxY + amount.y, maxZ + amount.z)
}

fun BoxView.expandedBy(negative: VectorView, positive: VectorView): BoxView {
    return Geometry.box(minX - negative.x, minY - negative.y, minZ - negative.z, maxX + positive.x, maxY + positive.y, maxZ + positive.z)
}

fun BoxView.translatedBy(offset: VectorView): BoxView {
    return Geometry.box(minX + offset.x, minY + offset.y, minZ + offset.z, maxX + offset.x, maxY + offset.y, maxZ + offset.z)
}

fun BoxView.movedTo(destination: VectorView): BoxView {
    return translatedBy(destination - center)
}

fun BoxView.union(point: VectorView): BoxView {
    return Geometry.box(minOf(minX, point.x), minOf(minY, point.y), minOf(minZ, point.z), maxOf(maxX, point.x), maxOf(maxY, point.y), maxOf(maxZ, point.z))
}

fun BoxView.union(other: BoxView): BoxView {
    return Geometry.box(minOf(minX, other.minX), minOf(minY, other.minY), minOf(minZ, other.minZ), maxOf(maxX, other.maxX), maxOf(maxY, other.maxY), maxOf(maxZ, other.maxZ))
}

fun BoxView.closestPointTo(point: VectorView): VectorView {
    return Geometry.vector(
        point.x.coerceIn(minX, maxX),
        point.y.coerceIn(minY, maxY),
        point.z.coerceIn(minZ, maxZ)
    )
}

fun BoxView.distanceSquaredTo(point: VectorView): Double {
    var distanceSquared = 0.0

    if (point.x < minX) distanceSquared += (point.x - minX).square()
    else if (point.x > maxX) distanceSquared += (point.x - maxX).square()

    if (point.y < minY) distanceSquared += (point.y - minY).square()
    else if (point.y > maxY) distanceSquared += (point.y - maxY).square()

    if (point.z < minZ) distanceSquared += (point.z - minZ).square()
    else if (point.z > maxZ) distanceSquared += (point.z - maxZ).square()

    return distanceSquared
}

fun BoxView.contains(point: VectorView): Boolean {
    return point.x > minX && point.x < maxX &&
            point.y > minY && point.y < maxY &&
            point.z > minZ && point.z < maxZ
}

fun BoxView.containsOrOn(point: VectorView): Boolean {
    return point.x in minX..maxX &&
            point.y in minY..maxY &&
            point.z in minZ..maxZ
}

fun BoxView.contains(other: BoxView): Boolean {
    return other.minX > minX && other.maxX < maxX &&
            other.minY > minY && other.maxY < maxY &&
            other.minZ > minZ && other.maxZ < maxZ
}

fun BoxView.containsOrOn(other: BoxView): Boolean {
    return other.minX in minX..maxX && other.maxX in minX..maxX &&
            other.minY in minY..maxY && other.maxY in minY..maxY &&
            other.minZ in minZ..maxZ && other.maxZ in minZ..maxZ
}

fun BoxView.containsHorizontal(point: VectorView): Boolean {
    return point.x > minX && point.x < maxX &&
            point.z > minZ && point.z < maxZ
}

fun BoxView.containsOrOnHorizontal(point: VectorView): Boolean {
    return point.x in minX..maxX &&
            point.z in minZ..maxZ
}

fun BoxView.containsHorizontal(other: BoxView): Boolean {
    return other.minX > minX && other.maxX < maxX &&
            other.minZ > minZ && other.maxZ < maxZ
}

fun BoxView.containsOrOnHorizontal(other: BoxView): Boolean {
    return other.minX in minX..maxX && other.maxX in minX..maxX &&
            other.minZ in minZ..maxZ && other.maxZ in minZ..maxZ
}

fun BoxView.intersects(other: BoxView): Boolean {
    return minX <= other.maxX && other.minX <= maxX &&
            minY <= other.maxY && other.minY <= maxY &&
            minZ <= other.maxZ && other.minZ <= maxZ
}

fun BoxView.intersectsHorizontal(other: BoxView): Boolean {
    return minX <= other.maxX && other.minX <= maxX &&
            minZ <= other.maxZ && other.minZ <= maxZ
}

fun BoxView.overlap(other: BoxView): BoxView {
    if (!intersects(other)) {
        return Geometry.box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    return Geometry.box(maxOf(minX, other.minX), maxOf(minY, other.minY), maxOf(minZ, other.minZ), minOf(maxX, other.maxX), minOf(maxY, other.maxY), minOf(maxZ, other.maxZ))
}

fun BoxView.vertices(): Array<VectorView> {
    return arrayOf(
        Geometry.vector(minX, minY, minZ),
        Geometry.vector(minX, minY, maxZ),
        Geometry.vector(minX, maxY, minZ),
        Geometry.vector(minX, maxY, maxZ),
        Geometry.vector(maxX, minY, minZ),
        Geometry.vector(maxX, minY, maxZ),
        Geometry.vector(maxX, maxY, minZ),
        Geometry.vector(maxX, maxY, maxZ)
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

/**
 * Describes the intersection interval between a ray and an axis-aligned box.
 *
 * [BoxIntersection.enterTime] and [BoxIntersection.exitTime] are expressed in the parameter space of [ray]:
 * `ray.pointAt(time)`. When the ray starts inside the box, [BoxIntersection.enterTime] is
 * `null` and the nearest hit point is the ray origin.
 *
 * If the intersection was produced by [BoxView.intersect] with a finite
 * `length`, the length is used only to decide whether the ray reaches the box.
 * The returned [BoxIntersection.exitTime] is still the real exit time from the box surface and
 * is not clipped to that length. Therefore [BoxIntersection.exitTime] and [BoxIntersection.exitPoint] may lie
 * beyond the requested ray length, especially when the ray starts inside the
 * box or the length ends before the ray leaves the box.
 */
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

    val origin = ray.origin
    val direction = ray.direction

    if (!testAxis(origin.x, direction.x, minX, maxX, -Vectors.UnitX, Vectors.UnitX)) return null
    if (!testAxis(origin.y, direction.y, minY, maxY, -Vectors.UnitY, Vectors.UnitY)) return null
    if (!testAxis(origin.z, direction.z, minZ, maxZ, -Vectors.UnitZ, Vectors.UnitZ)) return null

    if (exitTime < 0.0) return null
    if (maxTime != null && enterTime > maxTime) return null

    val startsInside = enterTime < 0.0

    return BoxIntersection(
        ray = ray,
        enterTime = if (startsInside) null else enterTime,
        exitTime = exitTime,
        enterNormal = if (startsInside) null else enterNormal,
        exitNormal = exitNormal ?: Vectors.Zero
    )
}
