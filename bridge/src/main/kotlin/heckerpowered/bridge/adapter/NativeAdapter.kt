/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter

interface NativeAdapter<in TNative, out TCommon> {
    fun adapt(native: TNative): TCommon
}