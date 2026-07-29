/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.adapter.client.render.ClientPostProcessRule
import heckerpowered.bridge.adapter.client.render.ClientWorldRenderRule
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.all
import heckerpowered.lethal.gameplay.client.render.BloomEffect
import heckerpowered.lethal.gameplay.client.render.BloomRenderRule
import heckerpowered.lethal.gameplay.client.render.ZeusChainEffect
import heckerpowered.lethal.gameplay.client.render.ZeusShotEffect
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientEntrypointInitializationTest {
    @Test
    fun repeatedEntrypointCallsDoNotRegisterDuplicateRenderRules() {
        val entrypoint = Entrypoint()

        entrypoint.onEntrypoint()
        entrypoint.onEntrypoint()

        assertEquals(expected = 1, actual = RuleRegistry.all<ClientPostProcessRule>().count { it === BloomTest })
        assertEquals(expected = 1, actual = RuleRegistry.all<ClientWorldRenderRule>().count { it === BloomEffect })
        assertEquals(expected = 1, actual = RuleRegistry.all<BloomRenderRule>().count { it === ZeusChainEffect })
        assertEquals(expected = 1, actual = RuleRegistry.all<BloomRenderRule>().count { it === ZeusShotEffect })
    }
}
