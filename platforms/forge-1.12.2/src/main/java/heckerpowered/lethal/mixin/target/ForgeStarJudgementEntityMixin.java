/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.lethal.gameplay.common.entity.ForgeStarJudgementEntity;
import heckerpowered.lethal.gameplay.common.skill.StarJudgementEntityAccess;
import heckerpowered.lethal.gameplay.common.skill.StarJudgementKind;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// TODO:
//
// We wrote the class.
// We wrote the interface.
// We own the getter,
// its name,
// its body,
// its return type,
// and every line between.
//
// Yet somewhere in the night,
// a mixin was summoned.
//
// A shadow was declared
// for a method already ours.
// A prefix was forged
// for an interface already ours.
// The bytecode transformer awoke,
// crossed the boundary of runtime,
// and rewrote our own creation
// so that it might become
// what we could have written
// in a single `implements` clause.
//
// O Mixin,
// scalpel for code we cannot touch,
// why have you been raised
// against code resting beneath our own hands?
//
// Was the source file locked?
// Was the class written by an enemy?
// Did `implements StarJudgementEntityAccess`
// demand a sacrifice
// the build system could not bear?
//
// No.
//
// We simply chose,
// with all sources open before us,
// to inject at runtime
// what could have existed at compile time.
//
// Delete this monument.
//
// Let the class implement the interface.
// Let the getter return the kind.
// Let the transformer sleep.
//
// And may we never again
// perform bytecode surgery
// on a patient
// whose source code
// is already lying open
// on the operating table.
@Mixin(value = ForgeStarJudgementEntity.class, remap = false)
@Implements(@Interface(iface = StarJudgementEntityAccess.class, prefix = "starJudgementEntityAccess$"))
abstract class ForgeStarJudgementEntityMixin {
    @Shadow
    @NotNull
    public abstract StarJudgementKind getKind();

    @NotNull
    public StarJudgementKind starJudgementEntityAccess$getStarJudgementKind() {
        return getKind();
    }
}
