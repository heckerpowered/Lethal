/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.entity.damagesource.DamageFeature;
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView;
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType;
import heckerpowered.bridge.math.VectorView;
import heckerpowered.bridge.resources.Identifier;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DamageSource.class)
@Implements(@Interface(iface = DamageSourceView.class, prefix = "damageSourceView$"))
abstract class DamageSourceMixin {
    @Shadow
    @Nullable
    public abstract Entity getDirectEntity();

    @Shadow
    @Nullable
    public abstract Entity getEntity();

    @Shadow
    @Nullable
    public abstract Vec3 getSourcePosition();

    @Shadow
    @NotNull
    public abstract Holder<DamageType> typeHolder();

    @Shadow
    public abstract boolean is(@NotNull net.minecraft.tags.TagKey<DamageType> damageTypeKey);

    @Nullable
    public EntityAccess damageSourceView$getDirectEntity() {
        return lethal$entity(getDirectEntity());
    }

    @Nullable
    public EntityAccess damageSourceView$getCausingEntity() {
        return lethal$entity(getEntity());
    }

    @Nullable
    public VectorView damageSourceView$getPosition() {
        final Vec3 position = getSourcePosition();
        if (position == null) {
            return null;
        }

        return MixinInterop.requireAccess(position, VectorView.class);
    }

    @NotNull
    public Identifier damageSourceView$getType() {
        final ResourceLocation location = typeHolder()
                .unwrapKey()
                .orElseThrow(() -> new IllegalStateException("Damage type is not registered"))
                .location();
        if (location.equals(DamageTypes.FELL_OUT_OF_WORLD.location())) {
            return VanillaDamageType.FellOutOfWorld.getIdentifier();
        }
        return MixinInterop.requireAccess(location, Identifier.class);
    }

    public boolean damageSourceView$has(@NotNull DamageFeature feature) {
        return switch (feature) {
            case DamagesHelmet -> is(DamageTypeTags.DAMAGES_HELMET);
            case BypassesArmor -> is(DamageTypeTags.BYPASSES_ARMOR);
            case BypassesShield -> is(DamageTypeTags.BYPASSES_SHIELD);
            case BypassesInvulnerability -> is(DamageTypeTags.BYPASSES_INVULNERABILITY);
            case BypassesEffects -> is(DamageTypeTags.BYPASSES_EFFECTS);
            case BypassesResistance -> is(DamageTypeTags.BYPASSES_RESISTANCE);
            case BypassesEnchantments -> is(DamageTypeTags.BYPASSES_ENCHANTMENTS);
            case BypassesWolfArmor -> is(DamageTypeTags.BYPASSES_WOLF_ARMOR);
            case Fire -> is(DamageTypeTags.IS_FIRE);
            case Projectile -> is(DamageTypeTags.IS_PROJECTILE);
            case Explosion -> is(DamageTypeTags.IS_EXPLOSION);
            case Fall -> is(DamageTypeTags.IS_FALL);
            case Drowning -> is(DamageTypeTags.IS_DROWNING);
            case Freezing -> is(DamageTypeTags.IS_FREEZING);
            case Lightning -> is(DamageTypeTags.IS_LIGHTNING);
            case PlayerAttack -> is(DamageTypeTags.IS_PLAYER_ATTACK);
            case WitchResistantTo -> is(DamageTypeTags.WITCH_RESISTANT_TO);
            case WitherImmuneTo -> is(DamageTypeTags.WITHER_IMMUNE_TO);
            case NoAnger -> is(DamageTypeTags.NO_ANGER);
            case NoImpact -> is(DamageTypeTags.NO_IMPACT);
            case NoKnockback -> is(DamageTypeTags.NO_KNOCKBACK);
            case AlwaysMostSignificantFall -> is(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL);
            case AlwaysKillsArmorStands -> is(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS);
            case CanBreakArmorStand -> is(DamageTypeTags.CAN_BREAK_ARMOR_STAND);
            case AlwaysTriggersSilverfish -> is(DamageTypeTags.ALWAYS_TRIGGERS_SILVERFISH);
            case AlwaysHurtsEnderDragons -> is(DamageTypeTags.ALWAYS_HURTS_ENDER_DRAGONS);
            case IgnitesArmorStands -> is(DamageTypeTags.IGNITES_ARMOR_STANDS);
            case BurnsArmorStands -> is(DamageTypeTags.BURNS_ARMOR_STANDS);
            case AvoidsGuardianThorns -> is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS);
            case BurnFromStepping -> is(DamageTypeTags.BURN_FROM_STEPPING);
            case PanicEnvironmentalCauses -> is(DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES);
            case PanicCauses -> is(DamageTypeTags.PANIC_CAUSES);
            case MaceSmash, SulfurCubeWithBlockImmuneTo -> false;
        };
    }

    @Nullable
    private static EntityAccess lethal$entity(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }

        return MixinInterop.requireAccess(entity, EntityAccess.class);
    }
}
