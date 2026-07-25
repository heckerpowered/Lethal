/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.network

import heckerpowered.bridge.network.PayloadTypeRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

class ModServerPlayNetworkingInitializationTest {
    @Test
    fun repeatedInitializationDoesNotRegisterDuplicatePayloadsOrReceivers() {
        ModServerPlayNetworking.onInitialize()
        ModServerPlayNetworking.onInitialize()

        assertTrue(PayloadTypeRegistry.ServerboundPlay.isRegistered(FireStatePayload.Type))
        assertTrue(PayloadTypeRegistry.ServerboundPlay.isRegistered(SkillActivationPayload.Type))
        assertTrue(PayloadTypeRegistry.ClientboundPlay.isRegistered(ZeusChainPayload.Type))
        assertTrue(PayloadTypeRegistry.ClientboundPlay.isRegistered(ZeusShotPayload.Type))
    }
}
