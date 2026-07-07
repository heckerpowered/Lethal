/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.math.BoxView
import heckerpowered.lethal.bridge.math.Geometry
import heckerpowered.lethal.bridge.math.VectorView
import heckerpowered.lethal.platform.interop.box
import heckerpowered.lethal.platform.interop.vector
import net.minecraft.entity.Entity
import java.util.*

class EntityAccessor(val entity: Entity) : EntityAccess {
    override val id: Int
        get() = entity.entityId

    override val uuid: UUID
        get() = entity.uniqueID

    override val position: VectorView
        get() = entity.positionVector.vector()

    override var velocity: VectorView
        get() = Geometry.vector(entity.motionX, entity.motionY, entity.motionZ)
        set(value) {
            entity.motionX = value.x
            entity.motionY = value.y
            entity.motionZ = value.z
        }

    override var boundingBox: BoxView
        get() = entity.entityBoundingBox.box()
        set(value) {
            entity.entityBoundingBox = value.box()
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
