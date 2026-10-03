/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render.zeus

import heckerpowered.bridge.adapter.client.render.context.WorldRenderContext
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.math.Vector
import heckerpowered.math.VectorView
import java.lang.reflect.Proxy
import kotlin.test.*

class ZeusShotLineTest {
    @Test
    fun frameInterpolationAndCameraIdentitySelectTheOriginalHandOffsets() {
        val player = player()
        val main = ZeusShotLine(player, Vector(0.0, 0.0, 1024.0), true, 0L)
        val off = main.copy(isMainHand = false)
        val first = context(0.5f, player)
        assertPosition(4.72, 66.42, 2.9, main.startPosition(first))
        assertPosition(5.28, 66.42, 2.9, off.startPosition(first))
        assertPosition(4.8, 66.32, 4.45, main.startPosition(context(0.5f, null)))
        assertPosition(4.8, 66.32, 4.45, main.startPosition(context(0.5f, player())))
        assertPosition(-0.28, 65.42, 0.9, main.startPosition(context(0f, player)))
        assertPosition(9.72, 67.42, 4.9, main.startPosition(context(1f, player)))
    }

    @Test
    fun shotAndChainOpacityKeepTheirIndependentOriginalDurations() {
        val shot = ZeusShotLine(player(), Vector(0.0, 0.0, 1024.0), true, 100L)
        val chain = ZeusChainLine(Vector(0.0, 0.0, 0.0), shot.endPosition, 100L)
        assertEquals(1f, shot.opacityAt(99L))
        assertEquals(0.5f, shot.opacityAt(40_000_100L))
        assertEquals(0f, shot.opacityAt(80_000_100L))
        assertEquals(0.5f, chain.opacityAt(50_000_100L))
        assertEquals(0f, chain.opacityAt(100_000_100L))
    }

    private fun player(): PlayerAccess = Proxy.newProxyInstance(PlayerAccess::class.java.classLoader, arrayOf(PlayerAccess::class.java)) { _, method, _ ->
        when (method.name) {
            "getPreviousPosition" -> Vector(0.0, 64.0, 0.0)
            "getPosition" -> Vector(10.0, 66.0, 4.0)
            "getPreviousPitch", "getPreviousYaw", "getPitch", "getYaw" -> 0.0
            "getEyeHeight" -> 1.62
            else -> error("Shot interpolation should not request ${method.name}")
        }
    } as PlayerAccess

    private fun context(partialTick: Float, cameraEntity: PlayerAccess?): WorldRenderContext =
        Proxy.newProxyInstance(WorldRenderContext::class.java.classLoader, arrayOf(WorldRenderContext::class.java)) { _, method, _ ->
            when (method.name) {
                "getPartialTick" -> partialTick
                "getFirstPersonViewEntity" -> cameraEntity
                else -> error("Shot origin should not request raster state: ${method.name}")
            }
        } as WorldRenderContext

    private fun assertPosition(x: Double, y: Double, z: Double, actual: VectorView) {
        assertEquals(x, actual.x, 1e-9)
        assertEquals(y, actual.y, 1e-9)
        assertEquals(z, actual.z, 1e-9)
    }
}
