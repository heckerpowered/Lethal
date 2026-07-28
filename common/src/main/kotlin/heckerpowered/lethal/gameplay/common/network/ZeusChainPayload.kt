/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.network

import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.network.ClientPlayNetworking.Context
import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.Payload
import heckerpowered.bridge.network.StreamBuffer
import heckerpowered.bridge.network.StreamCodecs
import heckerpowered.bridge.network.codec.StreamCodec
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.client.render.ZeusChainEffect

data class ZeusChainSegment(
    val startPosition: VectorView,
    val endPosition: VectorView,
)

class ZeusChainPayload(chainSegments: List<ZeusChainSegment>) : ClientboundPayload<ZeusChainPayload> {
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
        val Codec = StreamCodec.composite(ChainSegmentsCodec, ZeusChainPayload::chainSegments, ::ZeusChainPayload)
    }

    override val type: Payload.Type<ZeusChainPayload>
        get() = Type

    fun handle(context: Context) {
        ZeusChainEffect.play(chainSegments)
    }
}

private const val SEGMENT_BYTE_COUNT = 6 * Double.SIZE_BYTES
