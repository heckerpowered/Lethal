/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity.damagesource

import heckerpowered.bridge.QuantumRepresentation
import heckerpowered.bridge.VirtualRepresentation
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.platform.loadOrNull
import heckerpowered.bridge.resources.Identifier
import java.util.*

interface DamageSourceView {
    val directEntity: EntityAccess?
    val causingEntity: EntityAccess?
    val position: VectorView?

    val type: Identifier

    fun has(feature: DamageFeature): Boolean
}

sealed interface DamageSourceSpec

data class VirtualDamageSourceSpec(
    val type: Identifier,
    val features: EnumSet<DamageFeature>,
) : DamageSourceSpec, VirtualRepresentation

data class VanillaDamageSourceSpec(
    val type: VanillaDamageType,
) : DamageSourceSpec, QuantumRepresentation

data class VirtualDamageSource(
    val spec: VirtualDamageSourceSpec,

    override val directEntity: EntityAccess?,
    override val causingEntity: EntityAccess?,
    override val position: VectorView?,
) : DamageSourceView, VirtualRepresentation {
    override val type: Identifier
        get() = spec.type

    override fun has(feature: DamageFeature): Boolean {
        return spec.features.contains(feature)
    }
}

data class QuantumVanillaDamageSource(
    val source: VanillaDamageType,

    override val directEntity: EntityAccess?,
    override val causingEntity: EntityAccess?,
    override val position: VectorView?,
) : DamageSourceView, QuantumRepresentation {
    override val type: Identifier
        get() = source.identifier

    override fun has(feature: DamageFeature): Boolean {
        return source.has(feature)
    }
}

interface DamageProvider {
    fun source(spec: DamageSourceSpec, directEntity: EntityAccess? = null, causingEntity: EntityAccess? = null, position: VectorView? = null): DamageSourceView

    companion object {
        val Virtual: DamageProvider = VirtualDamageProvider
        val Hosting: DamageProvider?
            get() = Services.loadOrNull<DamageProvider>()
        val Auto
            get() = Hosting ?: Virtual
    }
}

object VirtualDamageProvider : DamageProvider {
    override fun source(spec: DamageSourceSpec, directEntity: EntityAccess?, causingEntity: EntityAccess?, position: VectorView?): DamageSourceView {
        return when (spec) {
            is VirtualDamageSourceSpec -> VirtualDamageSource(spec, directEntity, causingEntity, position)
            is VanillaDamageSourceSpec -> QuantumVanillaDamageSource(spec.type, directEntity, causingEntity, position)
        }
    }
}

object DamageSources {
    var Provider = DamageProvider.Auto

    fun source(spec: DamageSourceSpec, directEntity: EntityAccess? = null, causingEntity: EntityAccess? = null, position: VectorView? = null): DamageSourceView {
        return Provider.source(spec, directEntity, causingEntity, position)
    }

    fun vanilla(type: VanillaDamageType, directEntity: EntityAccess? = null, causingEntity: EntityAccess? = null, position: VectorView? = null): DamageSourceView {
        return Provider.source(VanillaDamageSourceSpec(type), directEntity, causingEntity, position)
    }
}
