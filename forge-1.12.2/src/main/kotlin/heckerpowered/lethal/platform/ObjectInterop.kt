package heckerpowered.lethal.platform

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.lethal.bridge.adapter.entity.damagesource.QuantumVanillaDamageSource
import heckerpowered.lethal.bridge.adapter.entity.damagesource.VirtualDamageSourceSpec
import heckerpowered.lethal.gameplay.common.entity.damagesource.VirtualDamageSource
import net.minecraft.entity.Entity
import net.minecraft.util.DamageSource

object ObjectInterop {
    fun source(source: DamageSourceView): DamageSource {
        if (source is DamageSource) return source
        if (source is QuantumVanillaDamageSource) {
            val virtualDamageType = source.source.toVirtualDamageType()
            return VirtualDamageSource(
                spec = VirtualDamageSourceSpec(
                    virtualDamageType.type,
                    virtualDamageType.features
                ),
                directEntity = entity(source.directEntity),
                causingEntity = entity(source.causingEntity),
                position = source.position?.let { GeometryInterop.vector(it) }
            )
        }
    }

    fun entity(entity: Entity): EntityAccess {
        return entity as? EntityAccess ?: error("TODO: Wrapper fallback")
    }

    fun entity(entity: Entity?): EntityAccess? {
        if (entity == null) return null
        return entity as? EntityAccess ?: error("TODO: Wrapper fallback")
    }

    fun entity(entity: EntityAccess): Entity {
        return entity as Entity
    }

    fun entity(entity: EntityAccess?): Entity? {
        return entity as? Entity
    }
}