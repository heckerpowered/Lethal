/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.network

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.network.ServerPlayNetworking
import heckerpowered.bridge.network.StreamCodecs
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register
import heckerpowered.lethal.gameplay.common.network.SkillActivationPayload
import heckerpowered.lethal.gameplay.common.skill.SkillActivationRequest
import heckerpowered.lethal.gameplay.common.skill.SkillActivationRule
import heckerpowered.lethal.gameplay.common.skill.SkillSlot
import io.netty.buffer.Unpooled
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SkillActivationPayloadTest {
    @Test
    fun codecPreservesEverySkillSlot() {
        for (slot in SkillSlot.entries) {
            val nativeBuffer = Unpooled.buffer()

            try {
                val streamBuffer = ByteBufStreamBuffer(nativeBuffer)
                SkillActivationPayload.Codec.encode(streamBuffer, SkillActivationPayload(slot))

                assertEquals(expected = slot, actual = SkillActivationPayload.Codec.decode(streamBuffer).slot)
                assertEquals(expected = 0, actual = nativeBuffer.readableBytes())
            } finally {
                nativeBuffer.release()
            }
        }
    }

    @Test
    fun codecRejectsUnknownSkillSlots() {
        val nativeBuffer = Unpooled.buffer()

        try {
            val streamBuffer = ByteBufStreamBuffer(nativeBuffer)
            StreamCodecs.Byte.encode(streamBuffer, 3.toByte())

            assertFailsWith<IllegalArgumentException> {
                SkillActivationPayload.Codec.decode(streamBuffer)
            }
        } finally {
            nativeBuffer.release()
        }
    }

    @Test
    fun handlerUsesTheServerPlayerFromTheNetworkContext() {
        val player = player()
        var receivedRequest: SkillActivationRequest? = null
        RuleRegistry.register<SkillActivationRule>(object : SkillActivationRule {
            override fun onSkillActivation(request: SkillActivationRequest) {
                receivedRequest = request
            }
        })

        SkillActivationPayload(SkillSlot.Secondary).handle(ServerPlayNetworking.Context(player))

        assertSame(player, receivedRequest?.player)
        assertEquals(expected = SkillSlot.Secondary, actual = receivedRequest?.slot)
    }

    private fun player(): PlayerAccess {
        return Proxy.newProxyInstance(
            PlayerAccess::class.java.classLoader,
            arrayOf(PlayerAccess::class.java),
        ) { instance, method, arguments ->
            when (method.name) {
                "isAlive" -> true
                "isRemoved" -> false
                "isSpectator" -> false
                "equals" -> instance === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> "SkillActivationPayloadTestPlayer"
                else -> error("Unexpected player access: ${method.name}")
            }
        } as PlayerAccess
    }
}
