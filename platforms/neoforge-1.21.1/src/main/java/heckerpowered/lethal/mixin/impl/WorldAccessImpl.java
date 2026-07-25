/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl;

import heckerpowered.bridge.adapter.block.BlockStateAccess;
import heckerpowered.bridge.adapter.effect.ParticleEffect;
import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.sound.SoundCategory;
import heckerpowered.bridge.adapter.sound.SoundPlayback;
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape;
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket;
import heckerpowered.bridge.math.BlockDirection;
import heckerpowered.bridge.math.BlockPositionView;
import heckerpowered.bridge.math.BoxView;
import heckerpowered.bridge.math.RayView;
import heckerpowered.bridge.math.VectorView;
import heckerpowered.bridge.resources.Identifier;
import heckerpowered.lethal.platform.interop.MixinInterop;
import heckerpowered.lethal.platform.interop.ParticleInterop;
import kotlin.sequences.Sequence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public final class WorldAccessImpl {
    private static final double NEAR_ZERO = 1.0E-8;

    private WorldAccessImpl() {
    }

    public static int loadedEntityCount(@NotNull LevelEntityGetter<Entity> entities) {
        return MixinInterop.requireAccess(entities, LevelEntityIndex.class).lethal$getLoadedEntityCount();
    }

    @NotNull
    public static Sequence<EntityAccess> entities(@NotNull Level level, @NotNull LevelEntityGetter<Entity> entities) {
        return () -> new EntityIterator(entities.getAll().iterator(), level.getPartEntities().iterator());
    }

    @NotNull
    public static Sequence<EntityAccess> getEntities(@NotNull Level level, @NotNull BoxView searchBox) {
        return () -> {
            final List<Entity> entities = level.getEntities((Entity) null, box(searchBox), ignored -> true);
            return new EntityIterator(entities.iterator(), Collections.emptyIterator());
        };
    }

    @NotNull
    public static Sequence<EntityRayBucket> getEntityRayBuckets(@NotNull Level level, @NotNull RayView ray, double length) {
        requireRayLength(length);
        final RaySegment segment = RaySegment.create(ray, length);
        if (segment == null) {
            return Collections::<EntityRayBucket>emptyIterator;
        }

        final double radius = level.getMaxEntityRadius();
        final AABB searchBox = new AABB(
                Math.min(segment.start.x, segment.end.x) - radius,
                Math.min(segment.start.y, segment.end.y) - radius,
                Math.min(segment.start.z, segment.end.z) - radius,
                Math.max(segment.start.x, segment.end.x) + radius,
                Math.max(segment.start.y, segment.end.y) + radius,
                Math.max(segment.start.z, segment.end.z) + radius
        );
        final Iterable<EntityAccess> candidates = () -> {
            final List<Entity> entities = level.getEntities((Entity) null, searchBox, ignored -> true);
            return new EntityIterator(entities.iterator(), Collections.emptyIterator());
        };
        return () -> Collections.singletonList(new EntityRayBucket(0.0, candidates)).iterator();
    }

    @NotNull
    public static Sequence<heckerpowered.bridge.adapter.world.raycast.BlockHitResult> raycastBlockHits(
            @NotNull Level level,
            @NotNull RayView ray,
            double distanceBlocks,
            @NotNull BlockRaycastShape shape
    ) {
        requireRayLength(distanceBlocks);
        return () -> {
            final RaySegment segment = RaySegment.create(ray, distanceBlocks);
            if (segment == null) {
                return Collections.emptyIterator();
            }
            return new BlockHitIterator(level, ray, segment, shape);
        };
    }

    public static boolean destroyBlock(@NotNull Level level, @NotNull BlockPositionView position, boolean dropItems) {
        return level.destroyBlock(blockPosition(position), dropItems);
    }

    public static void playSound(@NotNull Level level, @NotNull VectorView position, @NotNull SoundPlayback playback) {
        final Identifier identifier = playback.getSound().getIdentifier();
        final ResourceLocation location = identifier(identifier);
        final SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(location)
                .orElseThrow(() -> new IllegalArgumentException("Unregistered sound event: " + identifier.asString()));
        level.playSound(
                null,
                position.getX(),
                position.getY(),
                position.getZ(),
                sound,
                soundSource(playback.getCategory()),
                (float) playback.getVolume(),
                (float) playback.getPitch()
        );
    }

    public static void spawnParticles(@NotNull Level level, @NotNull VectorView position, @NotNull ParticleEffect effect) {
        final net.minecraft.core.particles.ParticleOptions particle = ParticleInterop.particle(effect);
        if (level instanceof ServerLevel serverLevel) {
            if (effect.getLongDistance()) {
                for (final var player : serverLevel.players()) {
                    serverLevel.sendParticles(
                            player,
                            particle,
                            true,
                            position.getX(),
                            position.getY(),
                            position.getZ(),
                            effect.getCount(),
                            effect.getPositionSpread().getX(),
                            effect.getPositionSpread().getY(),
                            effect.getPositionSpread().getZ(),
                            effect.getVelocitySpread()
                    );
                }
            } else {
                serverLevel.sendParticles(
                        particle,
                        position.getX(),
                        position.getY(),
                        position.getZ(),
                        effect.getCount(),
                        effect.getPositionSpread().getX(),
                        effect.getPositionSpread().getY(),
                        effect.getPositionSpread().getZ(),
                        effect.getVelocitySpread()
                );
            }
            return;
        }

        for (int index = 0; index < effect.getCount(); index++) {
            final double particleX = position.getX() + level.random.nextGaussian() * effect.getPositionSpread().getX();
            final double particleY = position.getY() + level.random.nextGaussian() * effect.getPositionSpread().getY();
            final double particleZ = position.getZ() + level.random.nextGaussian() * effect.getPositionSpread().getZ();
            final double velocityX = level.random.nextGaussian() * effect.getVelocitySpread();
            final double velocityY = level.random.nextGaussian() * effect.getVelocitySpread();
            final double velocityZ = level.random.nextGaussian() * effect.getVelocitySpread();
            level.addParticle(particle, effect.getLongDistance(), particleX, particleY, particleZ, velocityX, velocityY, velocityZ);
        }
    }

    private static void requireRayLength(double length) {
        if (!Double.isFinite(length) || length < 0.0) {
            throw new IllegalArgumentException("Raycast distance must be finite and non-negative");
        }
    }

    @NotNull
    private static AABB box(@NotNull BoxView box) {
        if (box instanceof AABB nativeBox) {
            return nativeBox;
        }
        return new AABB(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    @NotNull
    private static BlockPos blockPosition(@NotNull BlockPositionView position) {
        if (position instanceof BlockPos nativePosition) {
            return nativePosition;
        }
        return new BlockPos(position.getX(), position.getY(), position.getZ());
    }

    @NotNull
    private static ResourceLocation identifier(@NotNull Identifier identifier) {
        return MixinInterop.requireHost(identifier, ResourceLocation.class);
    }

    @NotNull
    private static SoundSource soundSource(@NotNull SoundCategory category) {
        return switch (category) {
            case Master -> SoundSource.MASTER;
            case Music -> SoundSource.MUSIC;
            case Records -> SoundSource.RECORDS;
            case Weather -> SoundSource.WEATHER;
            case Blocks -> SoundSource.BLOCKS;
            case Hostile -> SoundSource.HOSTILE;
            case Neutral -> SoundSource.NEUTRAL;
            case Players -> SoundSource.PLAYERS;
            case Ambient -> SoundSource.AMBIENT;
            case Voice -> SoundSource.VOICE;
        };
    }

    private static final class EntityIterator implements Iterator<EntityAccess> {
        private final Iterator<? extends Entity> entities;
        private final Iterator<? extends Entity> parts;

        private EntityIterator(Iterator<? extends Entity> entities, Iterator<? extends Entity> parts) {
            this.entities = entities;
            this.parts = parts;
        }

        @Override
        public boolean hasNext() {
            return entities.hasNext() || parts.hasNext();
        }

        @Override
        public EntityAccess next() {
            final Entity entity;
            if (entities.hasNext()) {
                entity = entities.next();
            } else if (parts.hasNext()) {
                entity = parts.next();
            } else {
                throw new NoSuchElementException();
            }
            return MixinInterop.requireAccess(entity, EntityAccess.class);
        }
    }

    private static final class RaySegment {
        private final Vec3 start;
        private final Vec3 end;

        private RaySegment(Vec3 start, Vec3 end) {
            this.start = start;
            this.end = end;
        }

        private static RaySegment create(@NotNull RayView ray, double distanceBlocks) {
            final VectorView origin = ray.getOrigin();
            final VectorView direction = ray.getDirection();
            final double directionLengthSquared = direction.getLengthSquared();
            if (!Double.isFinite(directionLengthSquared) || directionLengthSquared <= NEAR_ZERO * NEAR_ZERO) {
                return null;
            }
            if (!finite(origin) || !finite(direction)) {
                return null;
            }

            final double maximumTime = distanceBlocks / Math.sqrt(directionLengthSquared);
            final Vec3 start = new Vec3(origin.getX(), origin.getY(), origin.getZ());
            final Vec3 end = new Vec3(
                    origin.getX() + direction.getX() * maximumTime,
                    origin.getY() + direction.getY() * maximumTime,
                    origin.getZ() + direction.getZ() * maximumTime
            );
            return finite(end) ? new RaySegment(start, end) : null;
        }

        private static boolean finite(@NotNull VectorView vector) {
            return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY()) && Double.isFinite(vector.getZ());
        }

        private static boolean finite(@NotNull Vec3 vector) {
            return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
        }
    }

    private static final class BlockHitIterator implements Iterator<heckerpowered.bridge.adapter.world.raycast.BlockHitResult> {
        private final Level level;
        private final RayView ray;
        private final Vec3 start;
        private final Vec3 end;
        private final BlockRaycastShape shape;
        private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        private final int endBlockX;
        private final int endBlockY;
        private final int endBlockZ;
        private final int stepX;
        private final int stepY;
        private final int stepZ;
        private final double timePerBlockX;
        private final double timePerBlockY;
        private final double timePerBlockZ;
        private int blockX;
        private int blockY;
        private int blockZ;
        private double nextBoundaryTimeX;
        private double nextBoundaryTimeY;
        private double nextBoundaryTimeZ;
        private boolean firstBlock = true;
        private boolean exhausted;
        private heckerpowered.bridge.adapter.world.raycast.BlockHitResult next;

        private BlockHitIterator(@NotNull Level level, @NotNull RayView ray, @NotNull RaySegment segment, @NotNull BlockRaycastShape shape) {
            this.level = level;
            this.ray = ray;
            this.start = segment.start;
            this.end = segment.end;
            this.shape = shape;
            blockX = floor(start.x);
            blockY = floor(start.y);
            blockZ = floor(start.z);
            endBlockX = floor(end.x);
            endBlockY = floor(end.y);
            endBlockZ = floor(end.z);
            stepX = Integer.compare(endBlockX, blockX);
            stepY = Integer.compare(endBlockY, blockY);
            stepZ = Integer.compare(endBlockZ, blockZ);
            timePerBlockX = timePerBlock(ray.getDirection().getX());
            timePerBlockY = timePerBlock(ray.getDirection().getY());
            timePerBlockZ = timePerBlock(ray.getDirection().getZ());
            nextBoundaryTimeX = nextBoundaryTime(start.x, ray.getDirection().getX(), blockX, stepX);
            nextBoundaryTimeY = nextBoundaryTime(start.y, ray.getDirection().getY(), blockY, stepY);
            nextBoundaryTimeZ = nextBoundaryTime(start.z, ray.getDirection().getZ(), blockZ, stepZ);
        }

        @Override
        public boolean hasNext() {
            if (next == null && !exhausted) {
                advance();
            }
            return next != null;
        }

        @Override
        public heckerpowered.bridge.adapter.world.raycast.BlockHitResult next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final var result = next;
            next = null;
            return result;
        }

        private void advance() {
            while (!exhausted) {
                if (firstBlock) {
                    firstBlock = false;
                } else if (!stepToNextBlock()) {
                    exhausted = true;
                    return;
                }

                next = hitCurrentBlock();
                if (next != null) {
                    return;
                }

                if (blockX == endBlockX && blockY == endBlockY && blockZ == endBlockZ) {
                    exhausted = true;
                }
            }
        }

        private boolean stepToNextBlock() {
            if (blockX == endBlockX && blockY == endBlockY && blockZ == endBlockZ) {
                return false;
            }

            final double candidateX = blockX == endBlockX ? Double.POSITIVE_INFINITY : nextBoundaryTimeX;
            final double candidateY = blockY == endBlockY ? Double.POSITIVE_INFINITY : nextBoundaryTimeY;
            final double candidateZ = blockZ == endBlockZ ? Double.POSITIVE_INFINITY : nextBoundaryTimeZ;
            final double entryTime = Math.min(candidateX, Math.min(candidateY, candidateZ));
            if (!Double.isFinite(entryTime)) {
                throw new IllegalStateException("Block traversal could not reach the ray endpoint");
            }

            if (candidateX == entryTime) {
                blockX += stepX;
                nextBoundaryTimeX += timePerBlockX;
            }
            if (candidateY == entryTime) {
                blockY += stepY;
                nextBoundaryTimeY += timePerBlockY;
            }
            if (candidateZ == entryTime) {
                blockZ += stepZ;
                nextBoundaryTimeZ += timePerBlockZ;
            }
            return true;
        }

        private heckerpowered.bridge.adapter.world.raycast.BlockHitResult hitCurrentBlock() {
            position.set(blockX, blockY, blockZ);
            final BlockState state = level.getBlockState(position);
            VoxelShape nativeShape = switch (shape) {
                case Collision -> state.getCollisionShape(level, position, CollisionContext.empty());
                case Outline -> state.getShape(level, position, CollisionContext.empty());
            };
            if (nativeShape.isEmpty() && shape == BlockRaycastShape.Collision && state.is(Blocks.NETHER_PORTAL)) {
                nativeShape = state.getShape(level, position, CollisionContext.empty());
            }
            if (nativeShape.isEmpty()) {
                return null;
            }

            final net.minecraft.world.phys.BlockHitResult nativeHit = level.clipWithInteractionOverride(start, end, position, nativeShape, state);
            if (nativeHit == null) {
                return null;
            }

            final Vec3 point = nativeHit.getLocation();
            final VectorView direction = ray.getDirection();
            final VectorView origin = ray.getOrigin();
            final double time = ((point.x - origin.getX()) * direction.getX()
                    + (point.y - origin.getY()) * direction.getY()
                    + (point.z - origin.getZ()) * direction.getZ()) / direction.getLengthSquared();
            return new heckerpowered.bridge.adapter.world.raycast.BlockHitResult(
                    MixinInterop.requireAccess(position.immutable(), BlockPositionView.class),
                    MixinInterop.requireAccess(state, BlockStateAccess.class),
                    blockDirection(nativeHit.getDirection()),
                    MixinInterop.requireAccess(point, VectorView.class),
                    time
            );
        }

        private static int floor(double value) {
            final int integer = (int) value;
            return value < integer ? integer - 1 : integer;
        }

        private static double timePerBlock(double direction) {
            return direction == 0.0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(direction);
        }

        private static double nextBoundaryTime(double origin, double direction, int blockCoordinate, int step) {
            if (step == 0) {
                return Double.POSITIVE_INFINITY;
            }
            final double boundary = step > 0 ? blockCoordinate + 1.0 : blockCoordinate;
            final double time = (boundary - origin) / direction;
            return time == 0.0 ? 0.0 : time;
        }

        @NotNull
        private static BlockDirection blockDirection(@NotNull Direction direction) {
            return switch (direction) {
                case DOWN -> BlockDirection.Down;
                case UP -> BlockDirection.Up;
                case NORTH -> BlockDirection.North;
                case SOUTH -> BlockDirection.South;
                case WEST -> BlockDirection.West;
                case EAST -> BlockDirection.East;
            };
        }
    }
}
