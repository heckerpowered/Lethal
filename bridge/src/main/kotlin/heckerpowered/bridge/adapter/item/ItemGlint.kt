/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

import heckerpowered.bridge.adapter.item.stack.ItemStackAccess

/**
 * Optional item capability for stack-dependent enchanted-glint presentation.
 */
interface ItemGlint {
    /**
     * Returns whether [stack] should render with its host's enchanted glint.
     */
    fun hasGlint(stack: ItemStackAccess): Boolean
}
