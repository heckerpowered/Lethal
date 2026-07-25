/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services;

import heckerpowered.bridge.math.BlockPositionProvider;
import heckerpowered.bridge.math.BlockPositionView;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

public final class HostingBlockPositionProvider implements BlockPositionProvider {
    @Override
    @NotNull
    public BlockPositionView position(int x, int y, int z) {
        return MixinInterop.requireAccess(new BlockPos(x, y, z), BlockPositionView.class);
    }

    @Override
    @NotNull
    public BlockPositionView fromPackedLong(long value) {
        return MixinInterop.requireAccess(BlockPos.of(value), BlockPositionView.class);
    }

    @Override
    public long asLong(@NotNull BlockPositionView position) {
        return BlockPos.asLong(position.getX(), position.getY(), position.getZ());
    }
}
