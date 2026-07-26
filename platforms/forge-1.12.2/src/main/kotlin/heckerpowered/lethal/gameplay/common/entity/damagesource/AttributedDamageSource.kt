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
import heckerpowered.lethal.platform.interop.entityOrNull
import heckerpowered.lethal.platform.interop.vector
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.DamageSource
import net.minecraft.util.EntityDamageSource
import net.minecraft.util.EntityDamageSourceIndirect
import net.minecraft.util.math.Vec3d
import net.minecraft.util.text.ITextComponent

internal fun attributedDamageSource(spec: VanillaDamageSourceSpec, nativeSource: DamageSource, directEntity: EntityAccess?, causingEntity: EntityAccess?, position: VectorView?): DamageSourceView {
    val attribution = DamageAttribution(spec, nativeSource, directEntity, causingEntity, position)
    // Attribution must preserve information encoded by the native source type because host behavior may depend on it.
    return when (nativeSource) {
        is EntityDamageSourceIndirect -> AttributedIndirectDamageSource(attribution)
        is EntityDamageSource -> AttributedEntityDamageSource(attribution)
        else -> AttributedDamageSource(attribution)
    }
}

private class AttributedDamageSource(
    private val attribution: DamageAttribution,
) : DamageSource(attribution.originalSource.damageType),
    DamageSourceView by attribution {
    init {
        copyDamageFlagsFrom(attribution.originalSource)
    }

    override fun getImmediateSource(): Entity? {
        return attribution.nativeDirectEntity
    }

    override fun getTrueSource(): Entity? {
        return attribution.nativeCausingEntity
    }

    override fun getHungerDamage(): Float {
        return attribution.originalSource.hungerDamage
    }

    override fun isDifficultyScaled(): Boolean {
        return attribution.originalSource.isDifficultyScaled
    }

    override fun getDamageLocation(): Vec3d? {
        return attribution.damageLocation
    }

    override fun getDeathMessage(victim: EntityLivingBase): ITextComponent {
        return attribution.originalSource.getDeathMessage(victim)
    }
}

private class AttributedEntityDamageSource(
    private val attribution: DamageAttribution,
) : EntityDamageSource(attribution.originalSource.damageType, attribution.nativeDeathMessageEntity),
    DamageSourceView by attribution {
    init {
        copyDamageFlagsFrom(attribution.originalSource)
    }

    override fun getImmediateSource(): Entity? {
        return attribution.nativeDirectEntity
    }

    override fun getTrueSource(): Entity? {
        return attribution.nativeCausingEntity
    }

    override fun getHungerDamage(): Float {
        return attribution.originalSource.hungerDamage
    }

    override fun isDifficultyScaled(): Boolean {
        return attribution.originalSource.isDifficultyScaled
    }

    override fun getDamageLocation(): Vec3d? {
        return attribution.damageLocation
    }

    override fun getDeathMessage(victim: EntityLivingBase): ITextComponent {
        return if (attribution.hasAttributedEntity) super.getDeathMessage(victim) else attribution.originalSource.getDeathMessage(victim)
    }
}

private class AttributedIndirectDamageSource(
    private val attribution: DamageAttribution,
) : EntityDamageSourceIndirect(attribution.originalSource.damageType, attribution.nativeDirectEntity, attribution.nativeCausingEntity),
    DamageSourceView by attribution {
    init {
        copyDamageFlagsFrom(attribution.originalSource)
    }

    override fun getHungerDamage(): Float {
        return attribution.originalSource.hungerDamage
    }

    override fun isDifficultyScaled(): Boolean {
        return attribution.originalSource.isDifficultyScaled
    }

    override fun getDamageLocation(): Vec3d? {
        return attribution.damageLocation
    }

    override fun getDeathMessage(victim: EntityLivingBase): ITextComponent {
        return if (attribution.hasAttributedEntity) super.getDeathMessage(victim) else attribution.originalSource.getDeathMessage(victim)
    }
}

private class DamageAttribution(
    private val spec: VanillaDamageSourceSpec,
    val originalSource: DamageSource,
    override val directEntity: EntityAccess?,
    override val causingEntity: EntityAccess?,
    override val position: VectorView?,
) : DamageSourceView {
    val nativeDirectEntity = directEntity.entityOrNull()
    val nativeCausingEntity = causingEntity.entityOrNull()
    val nativeDeathMessageEntity = nativeCausingEntity ?: nativeDirectEntity
    val hasAttributedEntity = nativeDeathMessageEntity != null

    val damageLocation: Vec3d?
        get() = position?.vector() ?: nativeDirectEntity?.positionVector ?: originalSource.damageLocation

    override val type = spec.type.identifier

    override fun has(feature: DamageFeature): Boolean {
        return spec.type.has(feature)
    }
}

private fun DamageSource.copyDamageFlagsFrom(source: DamageSource) {
    if (source.isUnblockable) setDamageBypassesArmor()
    if (source.canHarmInCreative()) setDamageAllowedInCreativeMode()
    if (source.isDamageAbsolute) setDamageIsAbsolute()
    if (source.isFireDamage) setFireDamage()
    if (source.isProjectile) setProjectile()
    if (source.isMagicDamage) setMagicDamage()
    if (source.isExplosion) setExplosion()
    if (this is EntityDamageSource && source is EntityDamageSource && source.isThornsDamage) setIsThornsDamage()
}
