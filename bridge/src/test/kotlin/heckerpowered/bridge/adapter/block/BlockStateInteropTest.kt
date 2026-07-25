/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.block

import heckerpowered.bridge.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class BlockStateInteropTest {
    @Test
    fun classificationRemainsAnIndependentCapability() {
        assertFalse(BlockStateAccess::class.java.isAssignableFrom(BlockClassificationAccess::class.java))
    }

    @Test
    fun supportedClassificationResolvesToTheOriginalBlockState() {
        val blockState = ClassifiedBlockState

        assertSame(blockState, BlockStateInterop.classification(blockState))
    }

    @Test
    fun unsupportedClassificationResolvesToNull() {
        assertNull(BlockStateInterop.classification(OrdinaryBlockState))
    }

    @Test
    fun blockIdentityBelongsToTheBlockOwnedByTheState() {
        assertSame(ClassifiedBlock, ClassifiedBlockState.block)
    }

    private object ClassifiedBlockState : BlockStateAccess, BlockClassificationAccess {
        override val block = ClassifiedBlock
        override val isReplaceable = false

        override fun isIn(category: BlockCategory): Boolean {
            return false
        }
    }

    private object OrdinaryBlockState : BlockStateAccess {
        override val block = OrdinaryBlock
    }

    private object ClassifiedBlock : BlockAccess {
        override val identifier = Identifier.create("test", "classified")
    }

    private object OrdinaryBlock : BlockAccess {
        override val identifier = Identifier.create("test", "ordinary")
    }
}
