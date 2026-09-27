/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.resources.IdentifierProvider
import net.minecraft.resources.ResourceLocation

@Suppress("CAST_NEVER_SUCCEEDS")
fun Identifier.asHost(): ResourceLocation = this as? ResourceLocation ?: ResourceLocation.fromNamespaceAndPath(namespace, path)

@Suppress("CAST_NEVER_SUCCEEDS")
fun ResourceLocation.asView() = this as? Identifier ?: IdentifierProvider.Freestanding.identifier(namespace, path)
