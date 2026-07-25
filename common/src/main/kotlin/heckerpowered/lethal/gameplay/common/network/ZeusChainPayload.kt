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

data class ZeusChainSegment(val startPosition: VectorView, val endPosition: VectorView)

class ZeusChainPayload(chainSegments: List<ZeusChainSegment>) : ClientboundPayload<ZeusChainPayload> {
    val chainSegments = chainSegments.toList()

    init {
        require(this.chainSegments.isNotEmpty()) {
            "Zeus chain requires at least one segment"
        }
    }

    companion object {
        val PayloadId = Constants.identifier("zeus_chain")

        @JvmField
        val Type = Payload.Type<ZeusChainPayload>(PayloadId)

        private val vectorCodec = StreamCodec.composite(
            StreamCodecs.Double, { vector: VectorView -> vector.x },
            StreamCodecs.Double, { vector: VectorView -> vector.y },
            StreamCodecs.Double, { vector: VectorView -> vector.z },
            Geometry::vector,
        )
        private val chainSegmentCodec = StreamCodec.composite(
            vectorCodec, ZeusChainSegment::startPosition,
            vectorCodec, ZeusChainSegment::endPosition,
            ::ZeusChainSegment,
        )
        private val chainSegmentsCodec = StreamCodec.of<StreamBuffer, List<ZeusChainSegment>>(
            encoder = { output, segments ->
                require(segments.isNotEmpty()) {
                    "Zeus chain requires at least one segment"
                }

                StreamCodecs.VarInt.encode(output, segments.size)
                segments.forEach { segment -> chainSegmentCodec.encode(output, segment) }
            },
            decoder = { input ->
                val segmentCount = StreamCodecs.VarInt.decode(input)
                require(segmentCount >= 1) {
                    "Zeus chain segment count must be positive: $segmentCount"
                }

                val maximumReadableSegmentCount = input.readableByteCount / SEGMENT_BYTE_COUNT
                require(segmentCount <= maximumReadableSegmentCount) {
                    "Zeus chain segment count $segmentCount exceeds readable data for $maximumReadableSegmentCount segment(s)"
                }

                List(segmentCount) { chainSegmentCodec.decode(input) }
            },
        )
        val Codec = StreamCodec.composite(chainSegmentsCodec, ZeusChainPayload::chainSegments, ::ZeusChainPayload)
    }

    override val type: Payload.Type<ZeusChainPayload>
        get() = Type

    fun handle(context: Context) {
        ZeusChainEffect.play(chainSegments)
    }
}

private const val SEGMENT_BYTE_COUNT = 6 * Double.SIZE_BYTES
