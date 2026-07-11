/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network.codec

import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StreamCodecTest {
    @Test
    fun compositeEncodesAndDecodesFieldsInDeclarationOrder() {
        val codec = StreamCodec.composite(
            stringCodec, ChannelPayload::magicId,
            integerCodec, ChannelPayload::entityId,
            longCodec, ChannelPayload::channelTimeTicks,
            booleanCodec, ChannelPayload::clientPrediction,
            ::ChannelPayload,
        )
        val payload = ChannelPayload("matrix:magic", 42, 80L, true)
        val buffer = ValueBuffer()

        codec.encode(buffer, payload)

        assertEquals(listOf("matrix:magic", 42, 80L, true), buffer.values())
        assertEquals(payload, codec.decode(buffer))
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun mapTransformsBothCodecDirections() {
        val codec = integerCodec.map(Int::toString, String::toInt)
        val buffer = ValueBuffer()

        codec.encode(buffer, "27")

        assertEquals(listOf(27), buffer.values())
        assertEquals("27", codec.decode(buffer))
    }

    @Test
    fun mapStreamAdaptsAnotherBufferRepresentation() {
        val codec = integerCodec.mapStream(WrappedValueBuffer::buffer)
        val buffer = WrappedValueBuffer(ValueBuffer())

        codec.encode(buffer, 64)

        assertEquals(64, codec.decode(buffer))
    }

    @Test
    fun ofMemberUsesTheValueAsTheEncodingReceiver() {
        val codec = StreamCodec.ofMember<ValueBuffer, MemberEncodedValue>(
            encoder = MemberEncodedValue::encode,
            decoder = { input -> MemberEncodedValue(integerCodec.decode(input)) },
        )
        val value = MemberEncodedValue(19)
        val buffer = ValueBuffer()

        codec.encode(buffer, value)

        assertEquals(value, codec.decode(buffer))
    }

    @Test
    fun unitAcceptsOnlyItsDeclaredValue() {
        val codec = StreamCodec.unit<ValueBuffer, String>("ready")
        val buffer = ValueBuffer()

        codec.encode(buffer, "ready")

        assertEquals("ready", codec.decode(buffer))
        assertFailsWith<IllegalStateException> {
            codec.encode(buffer, "different")
        }
    }

    @Test
    fun dispatchSelectsTheCodecWrittenByItsTypeCodec() {
        val numberCodec = integerCodec.map(::NumberPayload, NumberPayload::number)
        val textCodec = stringCodec.map(::TextPayload, TextPayload::text)
        val codec = stringCodec.dispatch<DispatchedPayload>(
            typeGetter = DispatchedPayload::type,
            codecSelector = { type ->
                when (type) {
                    NumberPayload.type -> numberCodec
                    TextPayload.type -> textCodec
                    else -> error("Unknown dispatched payload type: $type")
                }
            },
        )
        val buffer = ValueBuffer()

        codec.encode(buffer, NumberPayload(12))
        codec.encode(buffer, TextPayload("payload"))

        assertEquals(listOf(NumberPayload.type, 12, TextPayload.type, "payload"), buffer.values())
        assertEquals(NumberPayload(12), codec.decode(buffer))
        assertEquals(TextPayload("payload"), codec.decode(buffer))
    }

    @Test
    fun recursiveBuildsSelfReferentialCodecsLazily() {
        val codec = StreamCodec.recursive<ValueBuffer, Tree> { recursiveCodec ->
            stringCodec.dispatch(
                typeGetter = { tree ->
                    when (tree) {
                        is Tree.Branch -> "branch"
                        is Tree.Leaf -> "leaf"
                    }
                },
                codecSelector = { type ->
                    when (type) {
                        "branch" -> StreamCodec.composite(
                            recursiveCodec, Tree.Branch::left,
                            recursiveCodec, Tree.Branch::right,
                            Tree::Branch,
                        )

                        "leaf" -> integerCodec.map(Tree::Leaf, Tree.Leaf::value)
                        else -> error("Unknown tree type: $type")
                    }
                },
            )
        }
        val tree = Tree.Branch(Tree.Leaf(3), Tree.Branch(Tree.Leaf(5), Tree.Leaf(8)))
        val buffer = ValueBuffer()

        codec.encode(buffer, tree)

        assertEquals(tree, codec.decode(buffer))
    }

    private data class ChannelPayload(
        val magicId: String,
        val entityId: Int,
        val channelTimeTicks: Long,
        val clientPrediction: Boolean,
    )

    private data class MemberEncodedValue(val value: Int) {
        fun encode(output: ValueBuffer) {
            output.write(value)
        }
    }

    private sealed interface DispatchedPayload {
        val type: String
    }

    private data class NumberPayload(val number: Int) : DispatchedPayload {
        override val type: String
            get() = NumberPayload.type

        companion object {
            const val type = "number"
        }
    }

    private data class TextPayload(val text: String) : DispatchedPayload {
        override val type: String
            get() = TextPayload.type

        companion object {
            const val type = "text"
        }
    }

    private sealed interface Tree {
        data class Branch(val left: Tree, val right: Tree) : Tree

        data class Leaf(val value: Int) : Tree
    }

    private data class TwelveFieldPayload(
        val first: Int,
        val second: Int,
        val third: Int,
        val fourth: Int,
        val fifth: Int,
        val sixth: Int,
        val seventh: Int,
        val eighth: Int,
        val ninth: Int,
        val tenth: Int,
        val eleventh: Int,
        val twelfth: Int,
    )

    private companion object {
        val integerCodec = storedValueCodec<Int>()
        val longCodec = storedValueCodec<Long>()
        val booleanCodec = storedValueCodec<Boolean>()
        val stringCodec = storedValueCodec<String>()
    }
}

private class ValueBuffer {
    private val storedValues = ArrayDeque<Any>()

    fun write(value: Any) {
        storedValues.addLast(value)
    }

    fun read(): Any {
        return storedValues.removeFirst()
    }

    fun values(): List<Any> {
        return storedValues.toList()
    }

    fun isEmpty(): Boolean {
        return storedValues.isEmpty()
    }
}

private data class WrappedValueBuffer(val buffer: ValueBuffer)

private inline fun <reified Value : Any> storedValueCodec(): StreamCodec<ValueBuffer, Value> {
    return StreamCodec.of(
        encoder = { output, value -> output.write(value) },
        decoder = { input ->
            val value = input.read()
            require(value is Value)
            value
        },
    )
}
