/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.entity.damagesource

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageFeature
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageSourceSpec
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.platform.interop.entityOrNull
import heckerpowered.lethal.platform.interop.vector
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.DamageSource
import net.minecraft.util.EntityDamageSource
import net.minecraft.util.EntityDamageSourceIndirect
import net.minecraft.util.math.Vec3d
import net.minecraft.util.text.ITextComponent

internal fun attributedDamageSource(
    spec: VanillaDamageSourceSpec,
    nativeSource: DamageSource,
    directEntity: EntityAccess?,
    causingEntity: EntityAccess?,
    position: VectorView?,
): DamageSourceView {
    val attribution = DamageAttribution(spec, nativeSource, directEntity, causingEntity, position)
    return if (nativeSource is EntityDamageSourceIndirect) {
        AttributedIndirectDamageSource(attribution)
    } else {
        AttributedDirectDamageSource(attribution)
    }
}

private class AttributedDirectDamageSource(
    private val attribution: DamageAttribution,
) : EntityDamageSource(
    attribution.nativeSource.damageType,
    attribution.causingNativeEntity ?: attribution.directNativeEntity,
), DamageSourceView by attribution {
    init {
        copyPropertiesFrom(attribution.nativeSource)
    }

    override fun getImmediateSource(): Entity? {
        return attribution.directNativeEntity
    }

    override fun getTrueSource(): Entity? {
        return attribution.causingNativeEntity
    }

    override fun getHungerDamage(): Float {
        return attribution.nativeSource.hungerDamage
    }

    override fun isDifficultyScaled(): Boolean {
        return attribution.nativeSource.isDifficultyScaled
    }

    override fun getDamageLocation(): Vec3d? {
        return attribution.damageLocation
    }

    override fun getDeathMessage(victim: EntityLivingBase): ITextComponent {
        if (!attribution.hasAttributedEntity) return attribution.nativeSource.getDeathMessage(victim)

        return super.getDeathMessage(victim)
    }
}

private class AttributedIndirectDamageSource(
    private val attribution: DamageAttribution,
) : EntityDamageSourceIndirect(
    attribution.nativeSource.damageType,
    attribution.directNativeEntity,
    attribution.causingNativeEntity,
), DamageSourceView by attribution {
    init {
        copyPropertiesFrom(attribution.nativeSource)
    }

    override fun getHungerDamage(): Float {
        return attribution.nativeSource.hungerDamage
    }

    override fun isDifficultyScaled(): Boolean {
        return attribution.nativeSource.isDifficultyScaled
    }

    override fun getDamageLocation(): Vec3d? {
        return attribution.damageLocation
    }

    override fun getDeathMessage(victim: EntityLivingBase): ITextComponent {
        if (!attribution.hasAttributedEntity) return attribution.nativeSource.getDeathMessage(victim)

        return super.getDeathMessage(victim)
    }
}

private class DamageAttribution(
    private val spec: VanillaDamageSourceSpec,
    val nativeSource: DamageSource,
    override val directEntity: EntityAccess?,
    override val causingEntity: EntityAccess?,
    override val position: VectorView?,
) : DamageSourceView {
    val directNativeEntity = directEntity.entityOrNull()
    val causingNativeEntity = causingEntity.entityOrNull()

    val hasAttributedEntity: Boolean
        get() = directNativeEntity != null || causingNativeEntity != null

    val damageLocation: Vec3d?
        get() {
            if (position != null) return position.vector()

            val directEntity = directNativeEntity
            if (directEntity != null) return Vec3d(directEntity.posX, directEntity.posY, directEntity.posZ)

            return nativeSource.damageLocation
        }

    override val type: Identifier
        get() = spec.type.identifier

    override fun has(feature: DamageFeature): Boolean {
        return spec.type.has(feature)
    }
}

private fun EntityDamageSource.copyPropertiesFrom(source: DamageSource) {
    if (source.isUnblockable) setDamageBypassesArmor()
    if (source.canHarmInCreative()) setDamageAllowedInCreativeMode()
    if (source.isDamageAbsolute) setDamageIsAbsolute()
    if (source.isFireDamage) setFireDamage()
    if (source.isProjectile) setProjectile()
    if (source.isMagicDamage) setMagicDamage()
    if (source.isExplosion) setExplosion()
    if ((source as? EntityDamageSource)?.isThornsDamage == true) setIsThornsDamage()
}
