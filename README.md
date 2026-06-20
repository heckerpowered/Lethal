# Lethal Mod for Minecraft

Lethal is a commissioned Minecraft mod that adds powerful weapons, armor, tools, and related gameplay mechanics.

The project name is temporary. The commissioner did not provide a final name, so the project is currently called Lethal,
reflecting its focus on high-impact combat equipment and systems.

# Abstract Game Framework

To improve portability across Minecraft versions, this project experiments with an Abstract Game Framework.

The first version of the framework was developed and integrated directly into the source code of the 1.12.2 branch.
Instead of letting gameplay logic depend directly on Minecraft classes, the framework exposes a smaller set of stable
game abstractions.

## Core Concept

The core idea is Inversion of Control.

Gameplay systems do not directly construct or depend on Minecraft-specific objects whenever possible. Instead, they
interact with abstract interfaces and platform services. The target Minecraft version provides the concrete
implementation.

For pure value objects such as vectors, boxes, rays, and rotations, the framework supports both freestanding
implementations and hosted implementations backed by native Minecraft objects. For platform-bound concepts such as
damage sources, entities, and worlds, construction is delegated to the host platform because these objects must
participate in Minecraft’s native runtime systems.

This design reduces version-specific code in gameplay logic and makes future migration easier. When targeting another
Minecraft version, most compatibility work can be isolated inside the framework and platform layer rather than spread
throughout the gameplay code.

# Technical Details

* Abstract gameplay interfaces for core game concepts.
* Freestanding vs. hosted object model for portable math and geometry types.
* Native Minecraft objects can host framework interfaces through Mixin.
* Platform services provide Minecraft-version-specific behavior.
* The 1.12.2 branch uses a modernized build and transformation pipeline with Kotlin, Mixin, ForgeGradle 7 legacy
  support, and remapping/reobfuscation integration.

# Credits

* [Heckerpowered](https://github.com/heckerpowered)
* [1zero0](https://github.com/1zero0)