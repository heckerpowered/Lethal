/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.material

import heckerpowered.render.engine.shader.program.FragmentOutput
import heckerpowered.render.pipeline.color.BlendFactor
import heckerpowered.render.pipeline.color.BlendState
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.color.blendState
import heckerpowered.render.resource.texture.TextureFormat

/**
 * Produces one color slot's RHI state from resolved source and destination facts.
 *
 * Source is null when the shader declares no output at that location; destination is null when
 * borrowed storage has no declared alpha contract. A writing result still needs a shader output.
 * alphaFromTexture has already been resolved in source, without changing allocation metadata.
 * Implementations must be immutable and deterministic, without allocating device resources or
 * retaining preparation inputs. Depth, ordering, attachment operations and lifetime are separate.
 */
fun interface CompositingMode {
    fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaMeaning?): ColorTargetState

    /**
     * Declares that [target]'s final blend equation and write mask preserve premultiplied coverage.
     *
     * The destination is assumed to contain valid premultiplied coverage and [source] is the
     * resolved shader output contract. The producer must guarantee finite alpha in [0,1] and
     * premultiplied RGB after each affected sample is written, including preserved channels.
     * This is a semantic promise, not a pixel check or an initialization claim.
     *
     * Preparation calls this at most once for a slot, only when its conservative equation checks
     * cannot prove preservation. [target] is the exact state already returned by [CompositingMode.target];
     * that method is not evaluated again. The answer must be deterministic and side-effect free.
     * Returning true cannot bypass shader/resource contracts, format checks or Discard validation.
     */
    fun preservesCoverage(source: FragmentOutput, target: ColorTargetState): Boolean = false

    object Replace : CompositingMode {
        override fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaMeaning?) = ColorTargetState(format)
    }

    /** Requires known coverage in alpha-bearing destinations; missing stored alpha is implicitly one. */
    object SourceOver : CompositingMode {
        override fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaMeaning?): ColorTargetState {
            val output = requireNotNull(source) { "SourceOver requires a declared fragment output" }
            require(output.alphaMeaning == AlphaMeaning.Coverage) { "SourceOver requires coverage input" }
            require(destination == AlphaMeaning.Coverage || !format.colorComponents.alpha) { "SourceOver requires a coverage destination" }

            val blend = when (output.representation) {
                AlphaRepresentation.Straight -> BlendState.StraightAlpha
                AlphaRepresentation.Premultiplied -> BlendState.PremultipliedAlpha
            }
            return ColorTargetState(format, blend)
        }
    }

    object Add : CompositingMode {
        override fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaMeaning?) = ColorTargetState(format, BlendState.Additive)
    }

    object AddColorPreserveAlpha : CompositingMode {
        private val blend = blendState {
            color(BlendFactor.One, BlendFactor.One)
            alpha(BlendFactor.Zero, BlendFactor.One)
        }

        override fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaMeaning?) = ColorTargetState(format, blend)
    }

    object SourceAlphaAdd : CompositingMode {
        private val blend = blendState {
            color(BlendFactor.SourceAlpha, BlendFactor.One)
            alpha(BlendFactor.SourceAlpha, BlendFactor.One)
        }

        override fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaMeaning?) = ColorTargetState(format, blend)
    }
}
