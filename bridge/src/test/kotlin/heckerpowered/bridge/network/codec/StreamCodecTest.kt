/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network.codec

import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class StreamCodecTest {
    @Test
    fun singleFieldCompositeUsesItsGetterAndConstructor() {
        val codec = StreamCodec.composite(IntegerCodec, SingleValue::value, ::SingleValue)

        assertRepresentation(codec, SingleValue(11), listOf(11))
    }

    @Test
    fun twoFieldCompositePreservesFieldOrder() {
        val codec = StreamCodec.composite(
            IntegerCodec, TwoValues::first,
            IntegerCodec, TwoValues::second,
            ::TwoValues,
        )

        assertRepresentation(codec, TwoValues(11, 23), listOf(11, 23))
    }

    @Test
    fun threeFieldCompositePreservesFieldOrder() {
        val codec = StreamCodec.composite(
            IntegerCodec, ThreeValues::first,
            IntegerCodec, ThreeValues::second,
            IntegerCodec, ThreeValues::third,
            ::ThreeValues,
        )

        assertRepresentation(codec, ThreeValues(11, 23, 37), listOf(11, 23, 37))
    }

    @Test
    fun fourFieldCompositePreservesFieldOrder() {
        val codec = StreamCodec.composite(
            IntegerCodec, FourValues::first,
            IntegerCodec, FourValues::second,
            IntegerCodec, FourValues::third,
            IntegerCodec, FourValues::fourth,
            ::FourValues,
        )

        assertRepresentation(codec, FourValues(11, 23, 37, 41), listOf(11, 23, 37, 41))
    }

    @Test
    fun compositeDoesNotConstructValuesFromTruncatedInput() {
        var constructionCount = 0
        val codec = StreamCodec.composite(
            IntegerCodec, FourValues::first,
            IntegerCodec, FourValues::second,
            IntegerCodec, FourValues::third,
            IntegerCodec, FourValues::fourth,
        ) { first, second, third, fourth ->
            constructionCount++
            FourValues(first, second, third, fourth)
        }
        val fields = listOf(11, 23, 37, 41)

        for (fieldCount in 0 until fields.size) {
            assertFailsWith<NoSuchElementException>("Available fields: $fieldCount") {
                codec.decode(ValueBuffer(fields.take(fieldCount)))
            }
            assertEquals(0, constructionCount)
        }
    }

    @Test
    fun mapAppliesOppositeTransformationsToEncodingAndDecoding() {
        val codec = IntegerCodec.map(
            decodeMapping = { it + 100 },
            encodeMapping = { it - 100 },
        )

        assertRepresentation(codec, 111, listOf(11))
    }

    @Test
    fun chainedMappingsComposeInOppositeOrders() {
        val codec = IntegerCodec
            .map(decodeMapping = { it + 100 }, encodeMapping = { it - 100 })
            .map(decodeMapping = { it * 3 }, encodeMapping = { it / 3 })

        assertRepresentation(codec, 333, listOf(11))
    }

    @Test
    fun failingEncodeMappingDoesNotInvokeTheUnderlyingEncoder() {
        val failure = IllegalArgumentException("Cannot represent this value")
        val codec = IntegerCodec.map<Int>(
            decodeMapping = { it },
            encodeMapping = { throw failure },
        )
        val output = ValueBuffer(listOf("previous-field"))

        val actualFailure = assertFailsWith<IllegalArgumentException> { codec.encode(output, 11) }

        assertSame(failure, actualFailure)
        assertEquals(listOf("previous-field"), output.values())
    }

    @Test
    fun failingDecodeMappingPreservesTheFollowingField() {
        val failure = IllegalArgumentException("Decoded value is invalid")
        val codec = IntegerCodec.map<Int>(
            decodeMapping = { throw failure },
            encodeMapping = { it },
        )
        val input = ValueBuffer(listOf(11, "next-field"))

        val actualFailure = assertFailsWith<IllegalArgumentException> { codec.decode(input) }

        assertSame(failure, actualFailure)
        assertEquals(listOf("next-field"), input.values())
    }

    @Test
    fun mapStreamUsesTheProvidedBackingBufferInBothDirections() {
        val codec = IntegerCodec.mapStream(WrappedValueBuffer::buffer)
        val output = WrappedValueBuffer(ValueBuffer(listOf("previous-field")))

        codec.encode(output, 11)

        assertEquals(listOf("previous-field", 11), output.buffer.values())
        val input = WrappedValueBuffer(ValueBuffer(listOf(23, "next-field")))
        assertEquals(23, codec.decode(input))
        assertEquals(listOf("next-field"), input.buffer.values())
    }

    @Test
    fun unitAcceptsEqualValuesWithoutWriting() {
        val codec = StreamCodec.unit<ValueBuffer, SingleValue>(SingleValue(11))
        val output = ValueBuffer(listOf("previous-field"))

        codec.encode(output, SingleValue(11))

        assertEquals(listOf("previous-field"), output.values())
    }

    @Test
    fun unitReturnsItsConfiguredInstanceWithoutReading() {
        val value = SingleValue(11)
        val codec = StreamCodec.unit<ValueBuffer, SingleValue>(value)
        val input = ValueBuffer(listOf("next-field"))

        assertSame(value, codec.decode(input))
        assertSame(value, codec.decode(input))
        assertEquals(listOf("next-field"), input.values())
        assertSame(value, codec.decode(ValueBuffer()))
    }

    @Test
    fun unitRejectsOtherValuesWithoutWriting() {
        val codec = StreamCodec.unit<ValueBuffer, SingleValue>(SingleValue(11))
        val output = ValueBuffer(listOf("previous-field"))

        assertFailsWith<IllegalStateException> { codec.encode(output, SingleValue(23)) }

        assertEquals(listOf("previous-field"), output.values())
    }

    @Test
    fun dispatchPrefixesEachVariantWithItsTypeTag() {
        val codec = valueCodec()

        assertRepresentation(codec, Value.Number(11), listOf(1, 11))
        assertRepresentation(codec, Value.Text("sample"), listOf(2, "sample"))
    }

    @Test
    fun dispatchDecodesAdjacentVariantsIndependently() {
        val codec = valueCodec()
        val input = ValueBuffer(listOf(1, 11, 2, "sample", "next-field"))

        assertEquals(Value.Number(11), codec.decode(input))
        assertEquals(listOf(2, "sample", "next-field"), input.values())
        assertEquals(Value.Text("sample"), codec.decode(input))
        assertEquals(listOf("next-field"), input.values())
    }

    @Test
    fun dispatchRejectsUnknownTagsWithoutReadingTheirBody() {
        val input = ValueBuffer(listOf(99, "unread-body"))

        assertFailsWith<IllegalArgumentException> { valueCodec().decode(input) }

        assertEquals(listOf("unread-body"), input.values())
    }

    @Test
    fun dispatchPropagatesFailuresFromTheSelectedCodec() {
        val failure = IllegalArgumentException("Invalid variant content")
        val failingCodec = StreamCodec.of<ValueBuffer, Value.Number>(
            encoder = { _, _ -> throw failure },
            decoder = { throw failure },
        )
        val codec = IntegerCodec.dispatch<Value.Number>({ 1 }) { failingCodec }

        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            codec.encode(ValueBuffer(), Value.Number(11))
        })
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            codec.decode(ValueBuffer(listOf(1)))
        })
    }

    @Test
    fun recursiveCodecBuildsLazilyOnceAndMatchesAFixedNestedRepresentation() {
        var factoryCalls = 0
        val codec = treeCodec { factoryCalls++ }
        val tree = Tree.Branch(Tree.Leaf(3), Tree.Branch(Tree.Leaf(5), Tree.Leaf(8)))

        assertEquals(0, factoryCalls)

        assertRepresentation(codec, tree, listOf(1, 0, 3, 1, 0, 5, 0, 8))
        assertEquals(1, factoryCalls)
        assertRepresentation(codec, Tree.Leaf(13), listOf(0, 13))
        assertEquals(1, factoryCalls)
    }

    @Test
    fun recursiveCodecCanInitializeFromDecodingAndReuseItsDelegateForEncoding() {
        var factoryCalls = 0
        val codec = treeCodec { factoryCalls++ }
        val input = ValueBuffer(listOf(1, 0, 3, 0, 5, "next-field"))

        assertEquals(0, factoryCalls)
        assertEquals(Tree.Branch(Tree.Leaf(3), Tree.Leaf(5)), codec.decode(input))
        assertEquals(listOf("next-field"), input.values())
        assertEquals(1, factoryCalls)

        val output = ValueBuffer()
        codec.encode(output, Tree.Leaf(8))
        assertEquals(listOf(0, 8), output.values())
        assertEquals(1, factoryCalls)
    }

    @Test
    fun recursiveCodecRejectsEveryTruncatedPrefixOfANestedValue() {
        val codec = treeCodec()
        val fields = listOf(1, 0, 3, 1, 0, 5, 0, 8)

        for (fieldCount in 0 until fields.size) {
            assertFailsWith<NoSuchElementException>("Available fields: $fieldCount") {
                codec.decode(ValueBuffer(fields.take(fieldCount)))
            }
        }
    }

    @Test
    fun recursiveCodecRejectsUnknownNestedTagsWithoutReadingTheirBody() {
        val input = ValueBuffer(listOf(1, 0, 3, 99, "unread-body"))

        assertFailsWith<IllegalArgumentException> { treeCodec().decode(input) }

        assertEquals(listOf("unread-body"), input.values())
    }

    private fun <Value> assertRepresentation(codec: StreamCodec<ValueBuffer, Value>, value: Value, fields: List<Any>) {
        val output = ValueBuffer(listOf("previous-field"))
        codec.encode(output, value)
        assertEquals(listOf("previous-field") + fields, output.values())

        val input = ValueBuffer(fields + "next-field")
        assertEquals(value, codec.decode(input))
        assertEquals(listOf("next-field"), input.values())
    }

    private fun valueCodec(): StreamCodec<ValueBuffer, Value> {
        val numberCodec = IntegerCodec.map(Value::Number, Value.Number::number)
        val textCodec = StringCodec.map(Value::Text, Value.Text::text)
        return IntegerCodec.dispatch<Value>(
            typeGetter = { value ->
                when (value) {
                    is Value.Number -> 1
                    is Value.Text -> 2
                }
            },
            codecSelector = { tag ->
                when (tag) {
                    1 -> numberCodec
                    2 -> textCodec
                    else -> throw IllegalArgumentException("Unknown value tag: $tag")
                }
            },
        )
    }

    private fun treeCodec(onInitialize: () -> Unit = {}): StreamCodec<ValueBuffer, Tree> {
        return StreamCodec.recursive { recursiveCodec ->
            onInitialize()
            val leafCodec = IntegerCodec.map(Tree::Leaf, Tree.Leaf::value)
            val branchCodec = StreamCodec.composite(
                recursiveCodec, Tree.Branch::left,
                recursiveCodec, Tree.Branch::right,
                Tree::Branch,
            )
            IntegerCodec.dispatch<Tree>(
                typeGetter = { tree ->
                    when (tree) {
                        is Tree.Leaf -> 0
                        is Tree.Branch -> 1
                    }
                },
                codecSelector = { tag ->
                    when (tag) {
                        0 -> leafCodec
                        1 -> branchCodec
                        else -> throw IllegalArgumentException("Unknown tree tag: $tag")
                    }
                },
            )
        }
    }

    private data class SingleValue(val value: Int)
    private data class TwoValues(val first: Int, val second: Int)
    private data class ThreeValues(val first: Int, val second: Int, val third: Int)
    private data class FourValues(val first: Int, val second: Int, val third: Int, val fourth: Int)

    private sealed interface Value {
        data class Number(val number: Int) : Value
        data class Text(val text: String) : Value
    }

    private sealed interface Tree {
        data class Leaf(val value: Int) : Tree
        data class Branch(val left: Tree, val right: Tree) : Tree
    }

    private companion object {
        val IntegerCodec = storedValueCodec<Int>()
        val StringCodec = storedValueCodec<String>()
    }
}

private class ValueBuffer(initialValues: List<Any> = emptyList()) {
    private val storedValues = ArrayDeque(initialValues)

    fun write(value: Any) {
        storedValues.addLast(value)
    }

    fun read(): Any = storedValues.removeFirst()

    fun values(): List<Any> = storedValues.toList()
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
