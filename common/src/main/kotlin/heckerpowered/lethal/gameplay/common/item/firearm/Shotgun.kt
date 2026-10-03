/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.math.*
import kotlin.math.*
import kotlin.random.Random

abstract class Shotgun : RayTraceGun() {
    protected abstract fun getPelletCount(player: PlayerAccess, weaponStack: ItemStackAccess): Int

    /** Returns the spread cone's half-angle in radians. */
    protected abstract fun getSpreadAngle(player: PlayerAccess, weaponStack: ItemStackAccess): Double

    override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long) {
        require(shotCount >= 0)
        if (shotCount == 0L) return

        val pelletCount = getPelletCount(player, weaponStack).also { require(it > 0) }

        val origin = player.eyePosition
        val forward = player.viewVector
        val spreadAngle = getSpreadAngle(player, weaponStack)
        val rays = generatePelletRays(origin, forward, pelletCount, spreadAngle)
        val distanceBlocks = getRayTraceDistanceBlocks(player, weaponStack)
        val entityHits = rays.mapNotNull { ray -> traceRay(player, weaponStack, ray, distanceBlocks).firstOrNull() }
        onRayTrace(player, weaponStack, shotCount, entityHits)
    }

    private fun generatePelletRays(origin: VectorView, forward: VectorView, pelletCount: Int, spreadAngle: Double): Sequence<RayView> {
        return (0 until pelletCount).asSequence().map { Ray(origin, samplePelletDirection(forward, spreadAngle)) }
    }

    protected open fun samplePelletDirection(forward: VectorView, spreadAngle: Double): VectorView {
        return pelletDirection(forward, spreadAngle, Random.nextDouble(), Random.nextDouble())
    }

    /**
     * Handles the first unobstructed hit of each pellet ray in this firing batch.
     * Each traversal of [entityHits] samples pellet directions and queries the world as it is consumed.
     * [shotCount] is the number of shots represented by the shared pellet distribution.
     */
    protected abstract fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>)

    /** Samples solid angle uniformly inside a cone around the player's view direction. */
    private fun pelletDirection(forward: VectorView, spreadAngle: Double, radialSample: Double, azimuthSample: Double): VectorView {
        require(spreadAngle.isFinite() && spreadAngle in 0.0..(PI / 2))
        require(radialSample in 0.0..1.0 && azimuthSample in 0.0..1.0)

        val direction = forward.normalized()
        val reference = if (abs(direction.y) < 0.99) Vector(0.0, 1.0, 0.0) else Vector(1.0, 0.0, 0.0)
        val right = direction.cross(reference).normalized()
        val up = right.cross(direction)
        val cosine = 1.0 - radialSample * (1.0 - cos(spreadAngle))
        val sine = sqrt((1.0 - cosine * cosine).coerceAtLeast(0.0))
        val azimuth = azimuthSample * 2.0 * PI

        return (direction * cosine + right * (sine * cos(azimuth)) + up * (sine * sin(azimuth))).normalized()
    }
}
