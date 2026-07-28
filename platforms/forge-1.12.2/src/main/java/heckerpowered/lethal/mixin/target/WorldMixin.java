/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.effect.ParticleEffect;
import heckerpowered.bridge.adapter.sound.SoundPlayback;
import heckerpowered.bridge.adapter.world.WorldAccess;
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult;
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape;
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket;
import heckerpowered.bridge.math.BoxView;
import heckerpowered.bridge.math.BlockPositionView;
import heckerpowered.bridge.math.RayView;
import heckerpowered.bridge.math.VectorView;
import heckerpowered.lethal.mixin.impl.WorldAccessImpl;
import kotlin.sequences.Sequence;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

@Mixin(World.class)
@Implements(@Interface(iface = WorldAccess.class, prefix = "worldAccess$"))
abstract class WorldMixin {
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

    @NotNull
    public Sequence<BlockHitResult> worldAccess$raycastBlockHits(@NotNull RayView ray, double distanceBlocks, @NotNull BlockRaycastShape shape) {
        return WorldAccessImpl.raycastBlockHits(self(), ray, distanceBlocks, shape);
    }

    public boolean worldAccess$destroyBlock(@NotNull BlockPositionView position, boolean dropItems) {
        return WorldAccessImpl.destroyBlock(self(), position, dropItems);
    }

    public void worldAccess$playSound(@NotNull VectorView position, @NotNull SoundPlayback playback) {
        WorldAccessImpl.playSound(self(), position, playback);
    }

    public void worldAccess$spawnParticles(@NotNull VectorView position, @NotNull ParticleEffect effect) {
        WorldAccessImpl.spawnParticles(self(), position, effect);
    }

    @NotNull
    public Sequence<EntityRayBucket> worldAccess$getEntityRayBuckets(@NotNull RayView ray, double length) {
        return WorldAccessImpl.getEntityRayBuckets(self(), ray, length);
    }
}
