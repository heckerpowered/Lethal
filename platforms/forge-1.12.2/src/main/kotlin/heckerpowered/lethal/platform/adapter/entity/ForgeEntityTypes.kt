/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.entity

import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.entity.ForgeEnhancedStarJudgementEntity
import heckerpowered.lethal.gameplay.common.entity.ForgeStandardStarJudgementEntity
import heckerpowered.lethal.platform.interop.identifier
import net.minecraftforge.fml.common.registry.EntityEntry
import net.minecraftforge.fml.common.registry.EntityEntryBuilder

object ForgeEntityTypes {
    private val entries by lazy {
        listOf(
            EntityEntryBuilder.create<ForgeStandardStarJudgementEntity>()
                .entity(ForgeStandardStarJudgementEntity::class.java)
                .id(Constants.identifier("star_judgement").identifier(), StandardStarJudgementNetworkId)
                .name("${Constants.MOD_ID}.star_judgement")
                .tracker(TrackingRangeBlocks, UpdateIntervalTicks, false)
                .build(),
            EntityEntryBuilder.create<ForgeEnhancedStarJudgementEntity>()
                .entity(ForgeEnhancedStarJudgementEntity::class.java)
                .id(Constants.identifier("enhanced_star_judgement").identifier(), EnhancedStarJudgementNetworkId)
                .name("${Constants.MOD_ID}.enhanced_star_judgement")
                .tracker(TrackingRangeBlocks, UpdateIntervalTicks, false)
                .build(),
        )
    }

    fun all(): List<EntityEntry> {
        return entries
    }

    private const val StandardStarJudgementNetworkId = 0
    private const val EnhancedStarJudgementNetworkId = 1

    // Legacy's client tracking range is ten chunks; Forge 1.12 expresses this value in blocks.
    private const val TrackingRangeBlocks = 10 * 16
    private const val UpdateIntervalTicks = 10
}
