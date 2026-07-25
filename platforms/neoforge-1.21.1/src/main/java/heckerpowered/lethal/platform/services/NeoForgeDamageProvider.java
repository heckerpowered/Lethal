/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services;

import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.entity.damagesource.DamageProvider;
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceSpec;
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView;
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageSourceSpec;
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType;
import heckerpowered.bridge.adapter.entity.damagesource.VirtualDamageSourceSpec;
import heckerpowered.bridge.math.VectorView;
import heckerpowered.bridge.resources.Identifier;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NeoForgeDamageProvider implements DamageProvider {
    @Override
    @NotNull
    public DamageSourceView source(
            @NotNull DamageSourceSpec spec,
            @Nullable EntityAccess directEntity,
            @Nullable EntityAccess causingEntity,
            @Nullable VectorView position
    ) {
        final Entity nativeDirectEntity = entityOrNull(directEntity);
        final Entity nativeCausingEntity = entityOrNull(causingEntity);
        final RegistryAccess registryAccess = registryAccess(nativeDirectEntity, nativeCausingEntity);
        final Registry<DamageType> damageTypes = registryAccess.registryOrThrow(Registries.DAMAGE_TYPE);
        final Holder<DamageType> damageType = damageTypes.getHolderOrThrow(damageTypeKey(spec));
        final Vec3 nativePosition = position == null
                ? null
                : new Vec3(position.getX(), position.getY(), position.getZ());
        final DamageSource source = new DamageSource(
                damageType,
                nativeDirectEntity,
                nativeCausingEntity,
                nativePosition
        );
        return MixinInterop.requireAccess(source, DamageSourceView.class);
    }

    @NotNull
    private static RegistryAccess registryAccess(@Nullable Entity directEntity, @Nullable Entity causingEntity) {
        final Entity attributedEntity = directEntity != null ? directEntity : causingEntity;
        if (attributedEntity != null) {
            return attributedEntity.registryAccess();
        }

        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("A damage source without an entity requires a running server registry");
        }
        return server.registryAccess();
    }

    @NotNull
    private static ResourceKey<DamageType> damageTypeKey(@NotNull DamageSourceSpec spec) {
        if (spec instanceof VanillaDamageSourceSpec vanillaSpec) {
            return vanillaDamageTypeKey(vanillaSpec.getType());
        }
        if (spec instanceof VirtualDamageSourceSpec virtualSpec) {
            return ResourceKey.create(Registries.DAMAGE_TYPE, resourceLocation(virtualSpec.getType()));
        }
        throw new IllegalArgumentException("Unsupported damage source specification: " + spec.getClass().getName());
    }

    @NotNull
    private static ResourceKey<DamageType> vanillaDamageTypeKey(@NotNull VanillaDamageType type) {
        return switch (type) {
            case Generic -> DamageTypes.GENERIC;
            case GenericKill -> DamageTypes.GENERIC_KILL;
            case FellOutOfWorld -> DamageTypes.FELL_OUT_OF_WORLD;
            case OutsideBorder -> DamageTypes.OUTSIDE_BORDER;
            case InFire -> DamageTypes.IN_FIRE;
            case OnFire -> DamageTypes.ON_FIRE;
            case Lava -> DamageTypes.LAVA;
            case HotFloor -> DamageTypes.HOT_FLOOR;
            case Campfire -> DamageTypes.CAMPFIRE;
            case DryOut -> DamageTypes.DRY_OUT;
            case SweetBerryBush -> DamageTypes.SWEET_BERRY_BUSH;
            case InWall -> DamageTypes.IN_WALL;
            case Cramming -> DamageTypes.CRAMMING;
            case Cactus -> DamageTypes.CACTUS;
            case FlyIntoWall -> DamageTypes.FLY_INTO_WALL;
            case Fall -> DamageTypes.FALL;
            case Stalagmite -> DamageTypes.STALAGMITE;
            case FallingBlock -> DamageTypes.FALLING_BLOCK;
            case FallingAnvil -> DamageTypes.FALLING_ANVIL;
            case FallingStalactite -> DamageTypes.FALLING_STALACTITE;
            case Drown -> DamageTypes.DROWN;
            case Starve -> DamageTypes.STARVE;
            case Freeze -> DamageTypes.FREEZE;
            case Magic -> DamageTypes.MAGIC;
            case IndirectMagic -> DamageTypes.INDIRECT_MAGIC;
            case Wither -> DamageTypes.WITHER;
            case DragonBreath -> DamageTypes.DRAGON_BREATH;
            case SonicBoom -> DamageTypes.SONIC_BOOM;
            case LightningBolt -> DamageTypes.LIGHTNING_BOLT;
            case Arrow -> DamageTypes.ARROW;
            case Trident -> DamageTypes.TRIDENT;
            case Thrown -> DamageTypes.THROWN;
            case MobProjectile -> DamageTypes.MOB_PROJECTILE;
            case Spit -> DamageTypes.SPIT;
            case WindCharge -> DamageTypes.WIND_CHARGE;
            case Fireball -> DamageTypes.FIREBALL;
            case UnattributedFireball -> DamageTypes.UNATTRIBUTED_FIREBALL;
            case WitherSkull -> DamageTypes.WITHER_SKULL;
            case Fireworks -> DamageTypes.FIREWORKS;
            case Explosion -> DamageTypes.EXPLOSION;
            case PlayerExplosion -> DamageTypes.PLAYER_EXPLOSION;
            case BadRespawnPoint -> DamageTypes.BAD_RESPAWN_POINT;
            case MobAttack -> DamageTypes.MOB_ATTACK;
            case MobAttackNoAggro -> DamageTypes.MOB_ATTACK_NO_AGGRO;
            case PlayerAttack -> DamageTypes.PLAYER_ATTACK;
            case Thorns -> DamageTypes.THORNS;
            case Sting -> DamageTypes.STING;
            case EnderPearl, Spear, MaceSmash -> throw new IllegalArgumentException(
                    "Minecraft 1.21.1 has no registered damage type for " + type
            );
        };
    }

    @NotNull
    private static ResourceLocation resourceLocation(@NotNull Identifier identifier) {
        return ResourceLocation.fromNamespaceAndPath(identifier.getNamespace(), identifier.getPath());
    }

    @Nullable
    private static Entity entityOrNull(@Nullable EntityAccess entity) {
        return entity == null ? null : MixinInterop.requireHost(entity, Entity.class);
    }
}
