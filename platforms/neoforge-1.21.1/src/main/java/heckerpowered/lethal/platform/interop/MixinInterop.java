/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop;

import org.jetbrains.annotations.NotNull;

public final class MixinInterop {
    private MixinInterop() {
    }

    @NotNull
    public static <Access> Access requireAccess(@NotNull Object host, @NotNull Class<Access> accessType) {
        if (!accessType.isInstance(host)) {
            throw new IllegalStateException(
                    "Required Mixin did not apply: " + host.getClass().getName() +
                            " does not implement " + accessType.getName()
            );
        }

        return accessType.cast(host);
    }

    @NotNull
    public static <Host> Host requireHost(@NotNull Object access, @NotNull Class<Host> hostType) {
        if (!hostType.isInstance(access)) {
            throw new IllegalArgumentException(
                    "Bridge access is not backed by the required NeoForge host type: " +
                            access.getClass().getName() + " is not " + hostType.getName()
            );
        }

        return hostType.cast(access);
    }
}
