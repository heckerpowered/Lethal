/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.resources.Identifier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ResourceLocation.class)
@Implements(@Interface(iface = Identifier.class, prefix = "identifier$"))
abstract class ResourceLocationMixin {
    @Shadow
    @NotNull
    public abstract String getNamespace();

    @Shadow
    @NotNull
    public abstract String getPath();

    @Intrinsic
    @NotNull
    public String identifier$getNamespace() {
        return getNamespace();
    }

    @Intrinsic
    @NotNull
    public String identifier$getPath() {
        return getPath();
    }
}
