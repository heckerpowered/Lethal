/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render

import heckerpowered.bridge.adapter.client.render.context.PostProcessContext
import heckerpowered.bridge.adapter.client.render.context.UiRenderContext
import heckerpowered.bridge.adapter.client.render.context.WorldRenderContext
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.forEach

object MinecraftRenderPipeline {
    fun renderWorld(context: WorldRenderContext) = with(context) {
        RuleRegistry.forEach<WorldRenderRule> { rule -> rule.onWorldRender() }
    }

    fun renderUi(context: UiRenderContext) = with(context) {
        RuleRegistry.forEach<UiRenderRule> { rule -> rule.onUiRender() }
    }

    fun renderPostProcess(context: PostProcessContext) = with(context) {
        RuleRegistry.forEach<PostProcessRenderRule> { rule -> rule.onPostProcess() }
    }
}

fun interface WorldRenderRule {
    context(context: WorldRenderContext)
    fun onWorldRender()
}

fun interface UiRenderRule {
    context(context: UiRenderContext)
    fun onUiRender()
}

fun interface PostProcessRenderRule {
    context(context: PostProcessContext)
    fun onPostProcess()
}