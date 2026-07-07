/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.adapter.entity

import heckerpowered.lethal.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.lethal.bridge.math.*
import java.util.*

interface EntityAccess : UniquelyIdentifiable {
    val id: Int
    override val uuid: UUID

    val position: VectorView
    var velocity: VectorView
    var boundingBox: BoxView

    var pitch: Double
    var yaw: Double

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

    val isAlive: Boolean
    val isRemoved: Boolean
    val isOnGround: Boolean
    val isOnFire: Boolean
}
