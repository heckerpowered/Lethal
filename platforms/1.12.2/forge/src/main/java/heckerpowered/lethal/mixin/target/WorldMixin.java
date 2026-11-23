/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.effect.ParticleEffect;
import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.sound.SoundPlayback;
import heckerpowered.bridge.adapter.world.WorldAccess;
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult;
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape;
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket;
import heckerpowered.bridge.math.BlockPositionView;
import heckerpowered.lethal.mixin.impl.WorldAccessImpl;
import heckerpowered.math.BoxView;
import heckerpowered.math.RayView;
import heckerpowered.math.VectorView;
import kotlin.sequences.Sequence;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(method = "getChunk(II)Lnet/minecraft/world/chunk/Chunk;", at = @At("HEAD"), cancellable = true)
    private void lethal$readLoadedRayChunk(int chunkX, int chunkZ, CallbackInfoReturnable<Chunk> callback) {
        WorldAccessImpl.readLoadedRayChunk(self(), chunkX, chunkZ, callback);
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

    @Nullable
    public Integer worldAccess$getSurfaceHeight(int blockX, int blockZ) {
        return WorldAccessImpl.getSurfaceHeight(self(), blockX, blockZ);
    }

    public boolean worldAccess$canTickEntityAt(@NotNull VectorView position) {
        return WorldAccessImpl.canTickEntityAt(self(), position);
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
