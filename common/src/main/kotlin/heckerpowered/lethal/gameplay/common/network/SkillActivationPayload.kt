/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.network

import heckerpowered.bridge.network.Payload
import heckerpowered.bridge.network.ServerPlayNetworking
import heckerpowered.bridge.network.ServerboundPayload
import heckerpowered.bridge.network.StreamCodecs
import heckerpowered.bridge.network.codec.StreamCodec
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.skill.SkillActivation
import heckerpowered.lethal.gameplay.common.skill.SkillActivationRequest
import heckerpowered.lethal.gameplay.common.skill.SkillSlot

class SkillActivationPayload(val slot: SkillSlot) : ServerboundPayload<SkillActivationPayload> {
    companion object {
        val PayloadId = Constants.identifier("skill_activation")
        val Type = Payload.Type<SkillActivationPayload>(PayloadId)
        private val skillSlotCodec = StreamCodecs.Byte.map(
            decodeMapping = SkillSlot::fromNetworkId,
            encodeMapping = SkillSlot::networkId,
        )
        val Codec = StreamCodec.composite(skillSlotCodec, SkillActivationPayload::slot, ::SkillActivationPayload)
    }

    override val type: Payload.Type<SkillActivationPayload>
        get() = Type

    fun handle(context: ServerPlayNetworking.Context) {
        SkillActivation.handle(SkillActivationRequest(context.player, slot))
    }
}
