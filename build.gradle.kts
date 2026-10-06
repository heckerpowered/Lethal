/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

plugins {
    // Share ModDev and idea-ext classes across host projects during IDEA sync.
    id("net.neoforged.moddev") version "2.+" apply false
}

allprojects {
    group = "heckerpowered.lethal"
}
