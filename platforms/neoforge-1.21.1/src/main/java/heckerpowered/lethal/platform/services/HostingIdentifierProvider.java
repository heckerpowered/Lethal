/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services;

import heckerpowered.bridge.resources.Identifier;
import heckerpowered.bridge.resources.IdentifierProvider;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public final class HostingIdentifierProvider implements IdentifierProvider {
    @Override
    @NotNull
    public Identifier identifier(@NotNull String namespace, @NotNull String path) {
        final ResourceLocation identifier = ResourceLocation.fromNamespaceAndPath(namespace, path);
        return MixinInterop.requireAccess(identifier, Identifier.class);
    }
}
