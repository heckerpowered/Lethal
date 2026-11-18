/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.form

import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.form.mining.Pickaxe

/**
 * A host-independent expression of an item, not just its category.
 *
 * Freestanding code cannot inspect vanilla item classes, so predefined forms such as [Pickaxe] expose
 * their categories independently of whether the host uses classes, tags, or components to describe them.
 * Custom items may instead use their blueprint as the form, allowing direct type or capability checks.
 * For example, a firearm can return itself as its form, so callers can check `item.form is Gun`.
 * A `HostedItem` retains the blueprint and exposes its form through [ItemAccess.form], preserving access
 * to that original item expression across the host boundary.
 * Shared forms such as [Regular] do not, by themselves, preserve an item's identity or reconstruct it.
 */
interface ItemForm
