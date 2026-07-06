/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.math.BoxView
import heckerpowered.lethal.bridge.math.Geometry
import heckerpowered.lethal.bridge.math.VectorView
import net.minecraft.entity.Entity
import java.util.*

class EntityAccessor(val entity: Entity) : EntityAccess {
    override val id: Int
        get() = entity.entityId

    override val uuid: UUID
        get() = entity.uniqueID

    override val position: VectorView
        get() = GeometryInterop.vector(entity.positionVector)

    override var velocity: VectorView
        get() = Geometry.vector(entity.motionX, entity.motionY, entity.motionZ)
        set(value) {
            entity.motionX = value.x
            entity.motionY = value.y
            entity.motionZ = value.z
        }

    override var boundingBox: BoxView
        get() = GeometryInterop.box(entity.entityBoundingBox)
        set(value) {
            entity.entityBoundingBox = GeometryInterop.box(value)
        }

    override var pitch: Double
        get() = entity.rotationPitch.toDouble()
        set(value) {
            entity.rotationPitch = value.toFloat()
        }

    override var yaw: Double
        get() = entity.rotationYaw.toDouble()
        set(value) {
            entity.rotationYaw = value.toFloat()
        }

    override val eyeHeight: Double
        get() = entity.eyeHeight.toDouble()

    override val isAlive: Boolean
        get() = entity.isEntityAlive

    override val isRemoved: Boolean
        get() = entity.isDead

    override val isOnGround: Boolean
        get() = entity.onGround

    override val isOnFire: Boolean
        get() = entity.isBurning
}
