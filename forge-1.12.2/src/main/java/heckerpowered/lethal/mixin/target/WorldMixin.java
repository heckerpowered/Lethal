/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess;
import heckerpowered.lethal.bridge.adapter.world.WorldAccess;
import heckerpowered.lethal.bridge.math.BoxView;
import heckerpowered.lethal.mixin.impl.WorldAccessImpl;
import heckerpowered.lethal.mixin.impl.WorldAccessImpl.ChunkAccess;
import kotlin.sequences.Sequence;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(World.class)
@Implements({
        @Interface(iface = WorldAccess.class, prefix = "worldAccess$"),
        @Interface(iface = ChunkAccess.class, prefix = "chunkAccess$")
})
abstract class WorldMixin {
    @Shadow
    protected abstract boolean isChunkLoaded(int x, int z, boolean allowEmpty);

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Unique
    private World self() {
        return (World) (Object) this;
    }

    @NotNull
    public Sequence<EntityAccess> worldAccess$getEntities() {
        return WorldAccessImpl.entities(self());
    }

    @NotNull
    public Sequence<EntityAccess> worldAccess$getEntities(@NotNull BoxView searchBox) {
        return WorldAccessImpl.getEntities(self(), searchBox);
    }

    public boolean chunkAccess$isChunkLoadedForEntitySearch(int chunkX, int chunkZ, boolean allowEmpty) {
        return isChunkLoaded(chunkX, chunkZ, allowEmpty);
    }
}
