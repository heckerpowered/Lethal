/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network.codec

typealias StreamEncoder<Buffer, Value> = (Buffer, Value) -> Unit
typealias StreamMemberEncoder<Buffer, Value> = (Value, Buffer) -> Unit
typealias StreamDecoder<Buffer, Value> = (Buffer) -> Value
typealias CodecOperation<Buffer, SourceValue, TargetValue> =
    (StreamCodec<Buffer, SourceValue>) -> StreamCodec<Buffer, TargetValue>

interface StreamCodec<Buffer, Value> {
    fun decode(input: Buffer): Value

    fun encode(output: Buffer, value: Value)

    fun <TargetValue> transform(operation: CodecOperation<Buffer, Value, TargetValue>): StreamCodec<Buffer, TargetValue> {
        return operation(this)
    }

    fun <TargetValue> map(decodeMapping: (Value) -> TargetValue, encodeMapping: (TargetValue) -> Value): StreamCodec<Buffer, TargetValue> {
        val sourceCodec = this
        return of(
            encoder = { output, value -> sourceCodec.encode(output, encodeMapping(value)) },
            decoder = { input -> decodeMapping(sourceCodec.decode(input)) },
        )
    }

    fun <TargetBuffer> mapStream(bufferMapping: (TargetBuffer) -> Buffer): StreamCodec<TargetBuffer, Value> {
        val sourceCodec = this
        return of(
            encoder = { output, value -> sourceCodec.encode(bufferMapping(output), value) },
            decoder = { input -> sourceCodec.decode(bufferMapping(input)) },
        )
    }

    fun <DispatchedValue> dispatch(
        typeGetter: (DispatchedValue) -> Value,
        codecSelector: (Value) -> StreamCodec<in Buffer, out DispatchedValue>,
    ): StreamCodec<Buffer, DispatchedValue> {
        val typeCodec = this
        return of(
            encoder = { output, value ->
                val type = typeGetter(value)
                val selectedCodec = codecSelector(type)
                typeCodec.encode(output, type)

                @Suppress("UNCHECKED_CAST")
                (selectedCodec as StreamCodec<Buffer, DispatchedValue>).encode(output, value)
            },
            decoder = { input ->
                val type = typeCodec.decode(input)
                codecSelector(type).decode(input)
            },
        )
    }

    fun <TargetBuffer : Buffer> cast(): StreamCodec<TargetBuffer, Value> {
        return mapStream { it }
    }

    companion object {
        fun <Buffer, Value> of(encoder: StreamEncoder<Buffer, Value>, decoder: StreamDecoder<Buffer, Value>): StreamCodec<Buffer, Value> {
            return object : StreamCodec<Buffer, Value> {
                override fun decode(input: Buffer): Value {
                    return decoder(input)
                }

                override fun encode(output: Buffer, value: Value) {
                    encoder(output, value)
                }
            }
        }

        fun <Buffer, Value> ofMember(encoder: StreamMemberEncoder<Buffer, Value>, decoder: StreamDecoder<Buffer, Value>): StreamCodec<Buffer, Value> {
            return of(
                encoder = { output, value -> encoder(value, output) },
                decoder = decoder,
            )
        }

        fun <Buffer, Value> unit(instance: Value): StreamCodec<Buffer, Value> {
            return object : StreamCodec<Buffer, Value> {
                override fun decode(input: Buffer): Value {
                    return instance
                }

                override fun encode(output: Buffer, value: Value) {
                    check(value == instance) { "Cannot encode $value, expected $instance" }
                }
            }
        }

        fun <Buffer, CompositeValue, FirstField> composite(
            firstCodec: StreamCodec<in Buffer, FirstField>,
            firstGetter: (CompositeValue) -> FirstField,
            constructor: (FirstField) -> CompositeValue,
        ): StreamCodec<Buffer, CompositeValue> {
            return of(
                encoder = { output, value ->
                    firstCodec.encode(output, firstGetter(value))
                },
                decoder = { input ->
                    val firstField = firstCodec.decode(input)
                    constructor(firstField)
                },
            )
        }

        fun <Buffer, CompositeValue, FirstField, SecondField> composite(
            firstCodec: StreamCodec<in Buffer, FirstField>,
            firstGetter: (CompositeValue) -> FirstField,
            secondCodec: StreamCodec<in Buffer, SecondField>,
            secondGetter: (CompositeValue) -> SecondField,
            constructor: (FirstField, SecondField) -> CompositeValue,
        ): StreamCodec<Buffer, CompositeValue> {
            return of(
                encoder = { output, value ->
                    firstCodec.encode(output, firstGetter(value))
                    secondCodec.encode(output, secondGetter(value))
                },
                decoder = { input ->
                    val firstField = firstCodec.decode(input)
                    val secondField = secondCodec.decode(input)
                    constructor(firstField, secondField)
                },
            )
        }

        fun <Buffer, CompositeValue, FirstField, SecondField, ThirdField> composite(
            firstCodec: StreamCodec<in Buffer, FirstField>,
            firstGetter: (CompositeValue) -> FirstField,
            secondCodec: StreamCodec<in Buffer, SecondField>,
            secondGetter: (CompositeValue) -> SecondField,
            thirdCodec: StreamCodec<in Buffer, ThirdField>,
            thirdGetter: (CompositeValue) -> ThirdField,
            constructor: (FirstField, SecondField, ThirdField) -> CompositeValue,
        ): StreamCodec<Buffer, CompositeValue> {
            return of(
                encoder = { output, value ->
                    firstCodec.encode(output, firstGetter(value))
                    secondCodec.encode(output, secondGetter(value))
                    thirdCodec.encode(output, thirdGetter(value))
                },
                decoder = { input ->
                    val firstField = firstCodec.decode(input)
                    val secondField = secondCodec.decode(input)
                    val thirdField = thirdCodec.decode(input)
                    constructor(firstField, secondField, thirdField)
                },
            )
        }

        fun <Buffer, CompositeValue, FirstField, SecondField, ThirdField, FourthField> composite(
            firstCodec: StreamCodec<in Buffer, FirstField>,
            firstGetter: (CompositeValue) -> FirstField,
            secondCodec: StreamCodec<in Buffer, SecondField>,
            secondGetter: (CompositeValue) -> SecondField,
            thirdCodec: StreamCodec<in Buffer, ThirdField>,
            thirdGetter: (CompositeValue) -> ThirdField,
            fourthCodec: StreamCodec<in Buffer, FourthField>,
            fourthGetter: (CompositeValue) -> FourthField,
            constructor: (FirstField, SecondField, ThirdField, FourthField) -> CompositeValue,
        ): StreamCodec<Buffer, CompositeValue> {
            return of(
                encoder = { output, value ->
                    firstCodec.encode(output, firstGetter(value))
                    secondCodec.encode(output, secondGetter(value))
                    thirdCodec.encode(output, thirdGetter(value))
                    fourthCodec.encode(output, fourthGetter(value))
                },
                decoder = { input ->
                    val firstField = firstCodec.decode(input)
                    val secondField = secondCodec.decode(input)
                    val thirdField = thirdCodec.decode(input)
                    val fourthField = fourthCodec.decode(input)
                    constructor(firstField, secondField, thirdField, fourthField)
                },
            )
        }

        fun <Buffer, Value> recursive(factory: CodecOperation<Buffer, Value, Value>): StreamCodec<Buffer, Value> {
            return RecursiveStreamCodec(factory)
        }
    }
}

private class RecursiveStreamCodec<Buffer, Value>(factory: CodecOperation<Buffer, Value, Value>) : StreamCodec<Buffer, Value> {
    private val delegate by lazy {
        factory(this)
    }

    override fun decode(input: Buffer): Value {
        return delegate.decode(input)
    }

    override fun encode(output: Buffer, value: Value) {
        delegate.encode(output, value)
    }
}
