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
            StringCodec, ChannelPayload::magicId,
            IntegerCodec, ChannelPayload::entityId,
            LongCodec, ChannelPayload::channelTimeTicks,
            BooleanCodec, ChannelPayload::clientPrediction,
            ::ChannelPayload,
        )
        val payload = ChannelPayload("matrix:magic", 42, 80L, true)
        val buffer = ValueBuffer()

        codec.encode(buffer, payload)

        assertEquals(expected = listOf("matrix:magic", 42, 80L, true), actual = buffer.values())
        assertEquals(expected = payload, actual = codec.decode(buffer))
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun mapTransformsBothCodecDirections() {
        val codec = IntegerCodec.map(Int::toString, String::toInt)
        val buffer = ValueBuffer()

        codec.encode(buffer, "27")

        assertEquals(expected = listOf(27), actual = buffer.values())
        assertEquals(expected = "27", actual = codec.decode(buffer))
    }

    @Test
    fun mapStreamAdaptsAnotherBufferRepresentation() {
        val codec = IntegerCodec.mapStream(WrappedValueBuffer::buffer)
        val buffer = WrappedValueBuffer(ValueBuffer())

        codec.encode(buffer, 64)

        assertEquals(expected = 64, actual = codec.decode(buffer))
    }

    @Test
    fun ofMemberUsesTheValueAsTheEncodingReceiver() {
        val codec = StreamCodec.ofMember<ValueBuffer, MemberEncodedValue>(MemberEncodedValue::encode) { input -> MemberEncodedValue(IntegerCodec.decode(input)) }
        val value = MemberEncodedValue(19)
        val buffer = ValueBuffer()

        codec.encode(buffer, value)

        assertEquals(expected = value, actual = codec.decode(buffer))
    }

    @Test
    fun unitAcceptsOnlyItsDeclaredValue() {
        val codec = StreamCodec.unit<ValueBuffer, String>("ready")
        val buffer = ValueBuffer()

        codec.encode(buffer, "ready")

        assertEquals(expected = "ready", actual = codec.decode(buffer))
        assertFailsWith<IllegalStateException> {
            codec.encode(buffer, "different")
        }
    }

    @Test
    fun dispatchSelectsTheCodecWrittenByItsTypeCodec() {
        val numberCodec = IntegerCodec.map(::NumberPayload, NumberPayload::number)
        val textCodec = StringCodec.map(::TextPayload, TextPayload::text)
        val codec = StringCodec.dispatch<DispatchedPayload>(DispatchedPayload::type) { type ->
            when (type) {
                NumberPayload.TYPE -> numberCodec
                TextPayload.TYPE -> textCodec
                else -> error("Unknown dispatched payload type: $type")
            }
        }
        val buffer = ValueBuffer()

        codec.encode(buffer, NumberPayload(12))
        codec.encode(buffer, TextPayload("payload"))

        assertEquals(expected = listOf(NumberPayload.TYPE, 12, TextPayload.TYPE, "payload"), actual = buffer.values())
        assertEquals(expected = NumberPayload(12), actual = codec.decode(buffer))
        assertEquals(expected = TextPayload("payload"), actual = codec.decode(buffer))
    }

    @Test
    fun recursiveBuildsSelfReferentialCodecsLazily() {
        val codec = StreamCodec.recursive<ValueBuffer, Tree> { recursiveCodec ->
            StringCodec.dispatch(
                { tree ->
                    when (tree) {
                        is Tree.Branch -> "branch"
                        is Tree.Leaf -> "leaf"
                    }
                },
                { type ->
                    when (type) {
                        "branch" -> StreamCodec.composite(
                            recursiveCodec, Tree.Branch::left,
                            recursiveCodec, Tree.Branch::right,
                            Tree::Branch,
                        )

                        "leaf" -> IntegerCodec.map(Tree::Leaf, Tree.Leaf::value)
                        else -> error("Unknown tree type: $type")
                    }
                },
            )
        }
        val tree = Tree.Branch(Tree.Leaf(3), Tree.Branch(Tree.Leaf(5), Tree.Leaf(8)))
        val buffer = ValueBuffer()

        codec.encode(buffer, tree)

        assertEquals(expected = tree, actual = codec.decode(buffer))
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
            get() = NumberPayload.TYPE

        companion object {
            const val TYPE = "number"
        }
    }

    private data class TextPayload(val text: String) : DispatchedPayload {
        override val type: String
            get() = TextPayload.TYPE

        companion object {
            const val TYPE = "text"
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
        val IntegerCodec = storedValueCodec<Int>()
        val LongCodec = storedValueCodec<Long>()
        val BooleanCodec = storedValueCodec<Boolean>()
        val StringCodec = storedValueCodec<String>()
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
        { output, value -> output.write(value) },
        { input ->
            val value = input.read()
            require(value is Value)
            value
        },
    )
}
