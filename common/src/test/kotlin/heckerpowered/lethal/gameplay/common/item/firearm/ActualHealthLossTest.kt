/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import kotlin.test.Test
import kotlin.test.assertEquals

class ActualHealthLossTest {
    @Test
    fun countsLossInsteadOfNominalDamageAndCapsOverkillAtAvailableHealth() {
        assertEquals(3.0, actualHealthLoss(10.0, 7.0))
        assertEquals(5.0, actualHealthLoss(5.0, -10.0))
        assertEquals(5.0, actualHealthLoss(5.0, -Double.MAX_VALUE))
        assertEquals(Double.MAX_VALUE, actualHealthLoss(Double.MAX_VALUE, -Double.MAX_VALUE))
    }

    @Test
    fun unchangedRestoredAndInvalidHealthDoNotCharge() {
        assertEquals(0.0, actualHealthLoss(10.0, 10.0))
        assertEquals(0.0, actualHealthLoss(10.0, 12.0))
        assertEquals(0.0, actualHealthLoss(-5.0, -10.0))
        assertEquals(0.0, actualHealthLoss(10.0, Double.NaN))
        assertEquals(0.0, actualHealthLoss(Double.POSITIVE_INFINITY, 0.0))
    }

}
