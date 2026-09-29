/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

import heckerpowered.bridge.adapter.BridgeAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.math.MinecraftDirections
import heckerpowered.bridge.math.toViewVector
import heckerpowered.math.*
import java.util.*

interface EntityAccess : BridgeAccess, UniquelyIdentifiable {
    val id: Int
    override val uuid: UUID

    val world: WorldAccess

    /**
     * Logical type of this entity. Physical multipart entities expose the type of their logical parent.
     */
    val type: EntityTypeAccess

    /**
     * Number of host ticks this entity has existed.
     */
    val tickCount: Int

    val previousPosition: VectorView
    var position: VectorView
    var velocity: VectorView
    var boundingBox: BoxView

    val previousPitch: Double
    val previousYaw: Double
    var pitch: Double
    var yaw: Double

    val previousRotation: RotatorView
        get() = Geometry.rotator(previousPitch, previousYaw)
    val rotation: RotatorView
        get() = Geometry.rotator(pitch, yaw)
    val viewVector: VectorView
        get() = rotation.toViewVector()

    val eyeHeight: Double
    val eyePosition: VectorView
        get() = position + MinecraftDirections.Up * eyeHeight
    val centerPosition: VectorView
        get() = boundingBox.center

    fun distanceSquaredTo(other: EntityAccess): Double {
        return position.distanceSquaredTo(other.position)
    }

    fun distanceTo(other: EntityAccess): Double {
        return position.distanceTo(other.position)
    }

    fun hurt(source: DamageSourceView, damagePoints: Double): Boolean
    fun remove()

    val isAlive: Boolean
    val isRemoved: Boolean
    val isOnGround: Boolean
    val isOnFire: Boolean
}

/**
 * Interpolates this entity's position for the current render tick.
 */
fun EntityAccess.interpolatePosition(partialTick: Float): VectorView {
    return previousPosition.interpolate(position, partialTick.toDouble())
}

/**
 * Interpolates this entity's eye position for the current render tick.
 */
fun EntityAccess.interpolateEyePosition(partialTick: Float): VectorView {
    return interpolatePosition(partialTick) + MinecraftDirections.Up * eyeHeight
}

/**
 * Interpolates this entity's rotation for the current render tick.
 */
fun EntityAccess.interpolateRotation(partialTick: Float): RotatorView {
    val alpha = partialTick.toDouble()
    val pitch = interpolate(previousPitch, pitch, alpha)
    val yaw = interpolate(previousYaw, yaw, alpha)
    return Geometry.rotator(pitch, yaw)
}

private fun interpolate(previousValue: Double, currentValue: Double, alpha: Double): Double {
    return previousValue + (currentValue - previousValue) * alpha
}