/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.network

import heckerpowered.bridge.adapter.asView
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.network.*
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.platform.interop.asView
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

abstract class ForgePayloadEnvelope(private val registry: PayloadTypeRegistry) : IMessage {
    private var containedPayload: Payload<*>? = null

    val payload: Payload<*>
        get() = checkNotNull(containedPayload) { "Packet envelope has not been initialized" }

    protected fun initialize(payload: Payload<*>) {
        check(containedPayload == null) { "Packet envelope has already been initialized" }
        containedPayload = payload
    }

    override fun fromBytes(nativeBuffer: ByteBuf) {
        val buffer = ByteBufStreamBuffer(nativeBuffer)
        val typeIdText = TypeIdCodec.decode(buffer)
        val untrustedTypeId = Identifier.parse(typeIdText)
        val payload = registry.decode(untrustedTypeId, buffer)
        check(buffer.readableByteCount == 0) { "Packet ${payload.type.id} left ${buffer.readableByteCount} unread byte(s)" }

        initialize(payload)
    }

    override fun toBytes(nativeBuffer: ByteBuf) {
        val buffer = ByteBufStreamBuffer(nativeBuffer)
        TypeIdCodec.encode(buffer, payload.type.id.asString())
        registry.encode(buffer, payload)
    }

    private companion object {
        val TypeIdCodec = StreamCodecs.stringUtf8(256)
    }
}

class ClientboundForgePayloadEnvelope() : ForgePayloadEnvelope(PayloadTypeRegistry.ClientboundPlay) {
    internal constructor(packet: ClientboundPayload<*>) : this() {
        initialize(packet)
    }
}

class ServerboundForgePayloadEnvelope() : ForgePayloadEnvelope(PayloadTypeRegistry.ServerboundPlay) {
    internal constructor(packet: ServerboundPayload<*>) : this() {
        initialize(packet)
    }
}

internal class ClientboundForgePacketEnvelopeHandler : IMessageHandler<ClientboundForgePayloadEnvelope, IMessage> {
    override fun onMessage(message: ClientboundForgePayloadEnvelope, context: MessageContext): IMessage? {
        message.payload
        val client = Minecraft.getMinecraft()
        client.addScheduledTask {
            val player = client.player ?: return@addScheduledTask
            val context = ClientPlayNetworking.Context(player.asView().asView<PlayerAccess>())
            ClientPlayNetworking.handle(message.payload, context)
        }
        return null
    }
}

internal class ServerboundForgePacketEnvelopeHandler : IMessageHandler<ServerboundForgePayloadEnvelope, IMessage> {
    override fun onMessage(message: ServerboundForgePayloadEnvelope, context: MessageContext): IMessage? {
        val player = context.serverHandler.player
        player.serverWorld.addScheduledTask {
            val context = ServerPlayNetworking.Context(player.asView().asView<PlayerAccess>())
            ServerPlayNetworking.handle(message.payload, context)
        }
        return null
    }
}