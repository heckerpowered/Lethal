/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.world.WorldAccess;
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket;
import heckerpowered.bridge.math.BoxView;
import heckerpowered.bridge.math.RayView;
import heckerpowered.lethal.mixin.impl.WorldAccessImpl;
import heckerpowered.lethal.mixin.impl.WorldAccessImpl.ChunkAccess;
import kotlin.sequences.Sequence;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

@Mixin(World.class)
@Implements({
        @Interface(iface = WorldAccess.class, prefix = "worldAccess$"),
        @Interface(iface = ChunkAccess.class, prefix = "chunkAccess$")
})
abstract class WorldMixin {
    @Shadow
    protected abstract boolean isChunkLoaded(int x, int z, boolean allowEmpty);

    @Shadow
    @Final
    public boolean isRemote;

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Unique
    private World self() {
        return (World) (Object) this;
    }

    public boolean worldAccess$isClientSide() {
        return isRemote;
    }

    public int worldAccess$getLoadedEntityCount() {
        return WorldAccessImpl.loadedEntityCount(self());
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

    @NotNull
    public Sequence<EntityRayBucket> worldAccess$getEntityRayBuckets(@NotNull RayView ray, double length) {
        return WorldAccessImpl.getEntityRayBuckets(self(), ray, length);
    }
}
