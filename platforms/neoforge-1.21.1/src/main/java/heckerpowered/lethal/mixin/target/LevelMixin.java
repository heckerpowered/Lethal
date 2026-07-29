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
import heckerpowered.bridge.math.BoxView;
import heckerpowered.bridge.math.RayView;
import heckerpowered.bridge.math.VectorView;
import heckerpowered.lethal.mixin.impl.WorldAccessImpl;
import kotlin.sequences.Sequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
@Implements(@Interface(iface = WorldAccess.class, prefix = "worldAccess$"))
abstract class LevelMixin {
    @Shadow
    public abstract boolean isClientSide();

    @Shadow
    @NotNull
    protected abstract LevelEntityGetter<Entity> getEntities();

    @Intrinsic
    public boolean worldAccess$isClientSide() {
        return isClientSide();
    }

    public int worldAccess$getLoadedEntityCount() {
        return WorldAccessImpl.loadedEntityCount(getEntities());
    }

    @NotNull
    public Sequence<EntityAccess> worldAccess$getEntities() {
        return WorldAccessImpl.entities(lethal$self(), getEntities());
    }

    @NotNull
    public Sequence<EntityAccess> worldAccess$getEntities(@NotNull BoxView searchBox) {
        return WorldAccessImpl.getEntities(lethal$self(), getEntities(), searchBox);
    }

    @NotNull
    public Sequence<BlockHitResult> worldAccess$raycastBlockHits(
            @NotNull RayView ray,
            double distanceBlocks,
            @NotNull BlockRaycastShape shape
    ) {
        return WorldAccessImpl.raycastBlockHits(lethal$self(), ray, distanceBlocks, shape);
    }

    public boolean worldAccess$destroyBlock(@NotNull BlockPositionView position, boolean dropItems) {
        return WorldAccessImpl.destroyBlock(lethal$self(), position, dropItems);
    }

    public void worldAccess$playSound(@NotNull VectorView position, @NotNull SoundPlayback playback) {
        WorldAccessImpl.playSound(lethal$self(), position, playback);
    }

    public void worldAccess$spawnParticles(@NotNull VectorView position, @NotNull ParticleEffect effect) {
        WorldAccessImpl.spawnParticles(lethal$self(), position, effect);
    }

    @NotNull
    public Sequence<EntityRayBucket> worldAccess$getEntityRayBuckets(@NotNull RayView ray, double length) {
        return WorldAccessImpl.getEntityRayBuckets(lethal$self(), getEntities(), ray, length);
    }

    @Unique
    private Level lethal$self() {
        return (Level) (Object) this;
    }
}
