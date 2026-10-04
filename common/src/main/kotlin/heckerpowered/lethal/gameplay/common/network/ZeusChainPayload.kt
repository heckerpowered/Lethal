/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.network

import heckerpowered.bridge.network.ClientPlayNetworking.Context
import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.Payload
import heckerpowered.bridge.network.StreamBuffer
import heckerpowered.bridge.network.StreamCodecs
import heckerpowered.bridge.network.codec.StreamCodec
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.client.render.zeus.ZeusBloodLaserEffect
import heckerpowered.lethal.gameplay.client.render.zeus.ZeusElectricLineEffect
import heckerpowered.lethal.gameplay.common.item.ZeusColors
import heckerpowered.math.Geometry
import heckerpowered.math.VectorView
import heckerpowered.render.color.Color

data class ZeusChainSegment(
    val startPosition: VectorView,
    val endPosition: VectorView,
)

class ZeusChainPayload(chainSegments: List<ZeusChainSegment>, val usesBloodLaser: Boolean = false, val color: Color = if (usesBloodLaser) ZeusColors.BloodLaser else ZeusColors.ElectricLine) : ClientboundPayload<ZeusChainPayload> {
    val chainSegments = chainSegments.toList()

    init {
        require(this.chainSegments.isNotEmpty()) { "Zeus chain requires at least one segment" }
    }

    companion object {
        val PayloadId = Constants.identifier("zeus_chain")

        @JvmField
        val Type = Payload.Type<ZeusChainPayload>(PayloadId)

        private val VectorCodec = StreamCodec.composite(
            StreamCodecs.Double, VectorView::x,
            StreamCodecs.Double, VectorView::y,
            StreamCodecs.Double, VectorView::z,
            Geometry::vector
        )
        private val ChainSegmentCodec = StreamCodec.composite(
            VectorCodec, ZeusChainSegment::startPosition,
            VectorCodec, ZeusChainSegment::endPosition,
            ::ZeusChainSegment
        )
        private val ChainSegmentsCodec = StreamCodec.of<StreamBuffer, List<ZeusChainSegment>>(
            { output, segments ->
                require(segments.isNotEmpty()) { "Zeus chain requires at least one segment" }

                StreamCodecs.VarInt.encode(output, segments.size)
                segments.forEach { ChainSegmentCodec.encode(output, it) }
            },
            { input ->
                val segmentCount = StreamCodecs.VarInt.decode(input)
                require(segmentCount >= 1) { "Zeus chain segment count must be positive: $segmentCount" }

                val maximumReadableSegmentCount = input.readableByteCount / SEGMENT_BYTE_COUNT
                require(segmentCount <= maximumReadableSegmentCount) { "Zeus chain segment count $segmentCount exceeds readable data for $maximumReadableSegmentCount segment(s)" }

                List(segmentCount) { ChainSegmentCodec.decode(input) }
            },
        )
        val Codec = StreamCodec.of<StreamBuffer, ZeusChainPayload>(
            { output, payload ->
                ChainSegmentsCodec.encode(output, payload.chainSegments)
                StreamCodecs.Boolean.encode(output, payload.usesBloodLaser)
                ZeusColorCodec.encode(output, payload.color)
            },
            { input ->
                val segments = ChainSegmentsCodec.decode(input)
                val usesBloodLaser = StreamCodecs.Boolean.decode(input)
                val color = if (input.readableByteCount == 0) {
                    if (usesBloodLaser) ZeusColors.BloodLaser else ZeusColors.ElectricLine
                } else ZeusColorCodec.decode(input)
                ZeusChainPayload(segments, usesBloodLaser, color)
            },
        )
    }

    override val type: Payload.Type<ZeusChainPayload>
        get() = Type

    fun handle(context: Context) {
        if (usesBloodLaser) ZeusBloodLaserEffect.play(chainSegments, color)
        else ZeusElectricLineEffect.play(chainSegments, color)
    }
}

private const val SEGMENT_BYTE_COUNT = 6 * Double.SIZE_BYTES
