package heckerpowered.lethal.bridge.adapter.entity.damagesource

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.math.VectorView

interface DamageSourceView {
    val id: String

    val directEntity: EntityAccess?
    val causingEntity: EntityAccess?
    val position: VectorView?
}

enum class VanillaDamageSource {
    Generic,
    Magic,
    InFire,
    OnFire,
    Lava,
    Drown,
    Fall,
    OutOfWorld,
    Cactus,
    Starve,
    InWall,
    FlyIntoWall,
    Anvil,
    FallingBlock,
    DragonBreath,
    LightningBolt,
    Explosion,
    PlayerExplosion,
    Mob,
    Player,
    Arrow,
    Thrown,
    IndirectMagic,
    Thorns
}