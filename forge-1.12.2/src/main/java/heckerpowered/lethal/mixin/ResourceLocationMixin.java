/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin;

import heckerpowered.lethal.bridge.resources.Identifier;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

@Mixin(ResourceLocation.class)
@Implements(@Interface(iface = Identifier.class, prefix = "identifier$"))
class ResourceLocationMixin {
    @Shadow
    @Final
    protected String namespace;

    @Shadow
    @Final
    protected String path;

    @Intrinsic
    @NotNull
    public String identifier$getNamespace() {
        return namespace;
    }

    @Intrinsic
    @NotNull
    public String identifier$getPath() {
        return path;
    }
}
