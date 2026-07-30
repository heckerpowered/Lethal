/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.math.Geometry
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityInterpolationTest {
    @Test
    fun interpolatesStableEntityStateForTheCurrentRenderTick() {
        val entity = entity()

        val position = entity.interpolatePosition(0.25F)
        val eyePosition = entity.interpolateEyePosition(0.25F)
        val rotation = entity.interpolateRotation(0.25F)

        assertEquals(expected = 1.0, actual = position.x)
        assertEquals(expected = 3.0, actual = position.y)
        assertEquals(expected = 5.0, actual = position.z)
        assertEquals(expected = 1.0, actual = eyePosition.x)
        assertEquals(expected = 4.5, actual = eyePosition.y)
        assertEquals(expected = 5.0, actual = eyePosition.z)
        assertEquals(expected = 0.0, actual = rotation.pitch)
        assertEquals(expected = 50.0, actual = rotation.yaw)
    }

    private fun entity(): EntityAccess {
        return Proxy.newProxyInstance(EntityAccess::class.java.classLoader, arrayOf(EntityAccess::class.java)) { _, method, _ ->
            when (method.name) {
                "getPreviousPosition" -> Geometry.vector(0.0, 2.0, 4.0)
                "getPosition" -> Geometry.vector(4.0, 6.0, 8.0)
                "getEyeHeight" -> 1.5
                "getPreviousPitch" -> -10.0
                "getPitch" -> 30.0
                "getPreviousYaw" -> 40.0
                "getYaw" -> 80.0
                else -> error("Unsupported EntityAccess method: ${method.name}")
            }
        } as EntityAccess
    }
}
