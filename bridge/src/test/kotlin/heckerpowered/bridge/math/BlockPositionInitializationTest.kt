/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import heckerpowered.bridge.FreestandingRepresentation
import heckerpowered.bridge.platform.Services
import java.net.URLClassLoader
import kotlin.test.Test

class BlockPositionInitializationTest {
    @Test
    fun freestandingProviderCanBeTheFirstEntryPoint() = runColdStart("freestandingProviderFirst")

    @Test
    fun providerCompanionCanBeTheFirstEntryPoint() = runColdStart("providerCompanionFirst")

    @Test
    fun facadeCanBeTheFirstEntryPoint() = runColdStart("facadeFirst")

    @Test
    fun defaultProviderCanBeTheFirstEntryPoint() = runColdStart("defaultProviderFirst")

    @Test
    fun automaticPackingCanBeTheFirstEntryPoint() = runColdStart("automaticPackingFirst")

    private fun runColdStart(entryPoint: String) {
        val locations = arrayOf(
            BlockPositionInitializationProbe::class.java.protectionDomain.codeSource.location,
            BlockPositionView::class.java.protectionDomain.codeSource.location,
            Services::class.java.protectionDomain.codeSource.location,
            Unit::class.java.protectionDomain.codeSource.location,
        )
        URLClassLoader(locations, null).use { loader ->
            val probe = Class.forName(BlockPositionInitializationProbe::class.java.name, true, loader)
            probe.getMethod(entryPoint).invoke(null)
        }
    }
}

internal object BlockPositionInitializationProbe {
    @JvmStatic
    fun freestandingProviderFirst() {
        val provider = FreestandingBlockPositionProvider
        verifyPosition(provider.position(12, -34, 56))
        verifyProviders()
    }

    @JvmStatic
    fun providerCompanionFirst() {
        val provider = BlockPositionProvider.Freestanding
        verifyPosition(provider.position(12, -34, 56))
        verifyProviders()
    }

    @JvmStatic
    fun facadeFirst() {
        verifyPosition(BlockPositions.of(12, -34, 56))
        verifyProviders()
    }

    @JvmStatic
    fun defaultProviderFirst() {
        val provider = object : BlockPositionProvider {}
        verifyPosition(provider.position(12, -34, 56))
        verifyPosition(provider.fromPackedLong(provider.asLong(provider.position(12, -34, 56))))
        val containing = provider.containing(1.9, -2.1, 3.0)
        check(containing.x == 1 && containing.y == -3 && containing.z == 3)
        verifyProviders()
    }

    @JvmStatic
    fun automaticPackingFirst() {
        val packed = BlockPositionPackingFormats.Auto.pack(12, -34, 56)
        verifyPosition(BlockPositions.fromPackedLong(packed))
        verifyProviders()
    }

    private fun verifyProviders() {
        check(BlockPositionProvider.Hosting == null)
        check(BlockPositionProvider.Freestanding === FreestandingBlockPositionProvider)
        check(BlockPositionProvider.Auto === FreestandingBlockPositionProvider)
        check(BlockPositions.Provider === FreestandingBlockPositionProvider)
        check(BlockPositions.Zero.isZero())
        check(BlockPositions.One.x == 1 && BlockPositions.One.y == 1 && BlockPositions.One.z == 1)
        check(BlockPositions.UnitX.x == 1 && BlockPositions.UnitX.y == 0 && BlockPositions.UnitX.z == 0)
        check(BlockPositions.UnitY.x == 0 && BlockPositions.UnitY.y == 1 && BlockPositions.UnitY.z == 0)
        check(BlockPositions.UnitZ.x == 0 && BlockPositions.UnitZ.y == 0 && BlockPositions.UnitZ.z == 1)
        verifyPosition(BlockPositions.of(12, -34, 56))
    }

    private fun verifyPosition(position: BlockPositionView) {
        check(position is FreestandingRepresentation)
        check(position.x == 12 && position.y == -34 && position.z == 56)
    }
}
