/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StarJudgementRendererTest {
    @Test
    fun renderDistanceUsesTheLegacyBlockCenterAndExclusiveLimit() {
        assertTrue(isWithinStarJudgementRenderDistance(0.0, 0.0, 0.5, 0.5))
        assertTrue(isWithinStarJudgementRenderDistance(0.1, 0.1, 512.49, 0.5))
        assertFalse(isWithinStarJudgementRenderDistance(0.1, 0.1, 512.5, 0.5))
    }
}
