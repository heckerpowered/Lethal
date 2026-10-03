/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader.reflection

import heckerpowered.render.shader.ShaderStage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class ShaderInterfaceArtifactTest {
    private fun artifact(
        inputs: List<ShaderInterfaceVariable> = emptyList(),
        resources: List<ShaderInterfaceResource> = emptyList(),
    ) = ShaderInterfaceArtifact(
        ShaderStage.Fragment, "main", "00".repeat(32), ShaderInterfaceDescription(inputs, emptyList(), resources),
    )

    @Test
    fun persistedTagsAndAllNumericFactsSurviveRoundTrip() {
        val inputs = listOf(
            ShaderInterfaceVariable("flag", null, ShaderValueDescription(ShaderScalarKind.Boolean, 1, 1, 1)),
            ShaderInterfaceVariable("signed", 2, ShaderValueDescription(ShaderScalarKind.SignedInteger, 16, 2, 1)),
            ShaderInterfaceVariable("unsigned", 3, ShaderValueDescription(ShaderScalarKind.UnsignedInteger, 64, 3, 1)),
            ShaderInterfaceVariable("float", 4, ShaderValueDescription(ShaderScalarKind.Float, 32, 4, 4, listOf(2, 0, null))),
        )
        val encoded = artifact(inputs).encode()
        assertContentEquals(byteArrayOf(0x52, 0x48, 0x49, 0x46, 0, 0, 0, 1, 0, 0, 0, 2), encoded.copyOfRange(0, 12))
        val decoded = ShaderInterfaceArtifact.decode(ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN))
        for (index in inputs.indices) {
            val expected = inputs[index]; val actual = decoded.description.inputs[index]
            assertEquals(expected.name, actual.name); assertEquals(expected.location, actual.location)
            assertEquals(expected.type.scalar, actual.type.scalar); assertEquals(expected.type.bitWidth, actual.type.bitWidth)
            assertEquals(expected.type.components, actual.type.components); assertEquals(expected.type.columns, actual.type.columns)
            assertEquals(expected.type.arrayDimensions, actual.type.arrayDimensions)
        }
        assertContentEquals(encoded, decoded.encode())
        assertEquals(listOf(1, 2, 3, 4), listOf(ShaderScalarKind.Boolean, ShaderScalarKind.SignedInteger, ShaderScalarKind.UnsignedInteger, ShaderScalarKind.Float).map { it.wireTag })
    }

    @Test
    fun factsSnapshotCallerListsAndCannotBeMutatedThroughJavaLists() {
        val dimensions = mutableListOf<Int?>(2)
        val type = ShaderValueDescription(ShaderScalarKind.Float, 32, 2, 1, dimensions)
        val inputs = mutableListOf(ShaderInterfaceVariable("original", 0, type))
        val description = ShaderInterfaceDescription(inputs, emptyList(), emptyList())
        dimensions.clear(); inputs.clear()
        assertEquals(listOf(2), type.arrayDimensions)
        assertEquals("original", description.inputs.single().name)
        assertFailsWith<UnsupportedOperationException> { (description.inputs as MutableList).clear() }
    }

    @Test
    fun fingerprintUsesRemainingCodeWithoutChangingItsPosition() {
        val bytes = ByteBuffer.wrap(byteArrayOf(0, 1, 2, 3)).apply { position(1) }
        val value = ShaderInterfaceArtifact(ShaderStage.Vertex, "main", ShaderInterfaceArtifact.fingerprint(bytes), ShaderInterfaceDescription(emptyList(), emptyList(), emptyList()))
        assertTrue(value.matchesSpirV(bytes)); assertEquals(1, bytes.position())
        assertFalse(value.matchesSpirV(ByteBuffer.wrap(byteArrayOf(1, 2, 4))))
    }

    @Test
    fun malformedHeadersLengthsUtf8AndTrailingDataAreRejected() {
        val valid = artifact().encode()
        fun changed(offset: Int, value: Int): ByteArray = valid.copyOf().also { ByteBuffer.wrap(it).putInt(offset, value) }
        for (invalid in listOf(changed(0, 0), changed(4, 2), changed(8, 99), changed(12, -1), changed(12, Int.MAX_VALUE), valid + byteArrayOf(0))) {
            assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact.decode(ByteBuffer.wrap(invalid)) }
        }
        for (length in valid.indices) assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact.decode(ByteBuffer.wrap(valid.copyOf(length))) }
        val invalidUtf8 = valid.copyOf().also { it[16] = 0xc0.toByte() }
        assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact.decode(ByteBuffer.wrap(invalidUtf8)) }
        assertFailsWith<IllegalArgumentException> { artifact().let { ShaderInterfaceArtifact(it.stage, "\ud800", it.spirVSha256, it.description).encode() } }
    }

    @Test
    fun imageTagsBlockLayoutAndTextBoundsAreChecked() {
        val scalar = ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1)
        val image = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 0, 3, listOf(2), null, null, emptyList(), ShaderImageDescription(ShaderImageDimension.Cube, false, true, false, scalar))
        val value = ShaderInterfaceArtifact(ShaderStage.Fragment, "main", "00".repeat(32), ShaderInterfaceDescription(emptyList(), emptyList(), listOf(image)))
        val valid = value.encode()
        val decoded = ShaderInterfaceArtifact.decode(ByteBuffer.wrap(valid)).description.resources.single()
        assertEquals(2L, decoded.descriptorCount)
        assertEquals(ShaderImageDimension.Cube, decoded.image?.dimension)
        assertTrue(assertNotNull(decoded.image).arrayed)
        val cursor = ByteBuffer.wrap(valid)
        cursor.position(12); val entryLength = cursor.int; cursor.position(cursor.position() + entryLength + 32 + 12)
        val resourceTagOffset = cursor.position()
        cursor.int; val nameLength = cursor.int; cursor.position(cursor.position() + nameLength + 8)
        val dimensionsCount = cursor.int; cursor.position(cursor.position() + dimensionsCount * 4 + 8 + 1 + 4 + 1)
        val imageTagOffset = cursor.position()
        for (offset in listOf(resourceTagOffset, imageTagOffset)) {
            val invalid = valid.copyOf(); ByteBuffer.wrap(invalid).putInt(offset, 999)
            assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact.decode(ByteBuffer.wrap(invalid)) }
        }
        assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact(ShaderStage.Vertex, "x".repeat(65537), "00".repeat(32), value.description).encode() }
        assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact.decode(ByteBuffer.allocate(16 * 1024 * 1024 + 1)) }
        val excessiveCount = ShaderInterfaceResource(image.kind, image.name, image.set, image.binding, listOf(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE), null, null, emptyList(), image.image)
        assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact(value.stage, value.entryPoint, value.spirVSha256, ShaderInterfaceDescription(emptyList(), emptyList(), listOf(excessiveCount))).encode() }
        val member = ShaderInterfaceBlockMember("x", 4, 4, scalar, 0, 0, false)
        val block = ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "params", null, null, emptyList(), 4, "Params", listOf(member), null)
        assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact(ShaderStage.Fragment, "main", "00".repeat(32), ShaderInterfaceDescription(emptyList(), emptyList(), listOf(block))).encode() }
    }

    @Test
    fun encoderRejectsInvalidCallerValueShapesAndOptionalFields() {
        val scalar = ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1)
        val invalidTypes = listOf(
            ShaderValueDescription(ShaderScalarKind.Boolean, -1, 1, 1),
            ShaderValueDescription(ShaderScalarKind.Boolean, 65, 1, 1),
            ShaderValueDescription(ShaderScalarKind.Float, 0, 1, 1),
            ShaderValueDescription(ShaderScalarKind.Float, 24, 1, 1),
            ShaderValueDescription(ShaderScalarKind.Float, 32, 0, 1),
            ShaderValueDescription(ShaderScalarKind.Float, 32, 5, 1),
            ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 0),
            ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 5),
            ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1, listOf(-2)),
            ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1, List(9) { 1 }),
        )
        for (type in invalidTypes) {
            assertFailsWith<IllegalArgumentException> { artifact(listOf(ShaderInterfaceVariable("input", 0, type))).encode() }
        }
        assertFailsWith<IllegalArgumentException> { artifact(listOf(ShaderInterfaceVariable("input", -2, scalar))).encode() }
        assertFailsWith<IllegalArgumentException> {
            ShaderInterfaceArtifact(ShaderStage.Vertex, "", "00".repeat(32), artifact().description).encode()
        }
        for (hash in listOf("00", "0g".repeat(32), "AA".repeat(32))) {
            assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact(ShaderStage.Vertex, "main", hash, artifact().description).encode() }
        }
        val image = ShaderImageDescription(ShaderImageDimension.TwoDimensional, false, false, false, scalar)
        for ((set, binding, dimensions) in listOf(
            Triple(-2, 0, emptyList()),
            Triple(0, -2, emptyList()),
            Triple(0, 0, listOf(-2)),
            Triple(0, 0, List(9) { 1 }),
            Triple(0, 0, listOf(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)),
        )) {
            val resource = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", set, binding, dimensions, null, null, emptyList(), image)
            assertFailsWith<IllegalArgumentException> { artifact(resources = listOf(resource)).encode() }
        }
    }

    @Test
    fun encoderRejectsInvalidCallerMemberLayoutsAndResourceRelationships() {
        val scalar = ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1)
        val member = ShaderInterfaceBlockMember("value", 0, 4, scalar, 0, 0, false)
        val matrix = ShaderValueDescription(ShaderScalarKind.Float, 32, 4, 4)
        val array = ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1, listOf(2))
        val nestedArray = ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1, listOf(2, 2))
        val invalidMembers = listOf(
            member.copy(offsetBytes = -1),
            member.copy(sizeBytes = -1),
            member.copy(offsetBytes = 4, sizeBytes = Long.MAX_VALUE),
            member.copy(matrixStrideBytes = -1),
            member.copy(arrayStrideBytes = -1),
            member.copy(matrixStrideBytes = 4),
            member.copy(arrayStrideBytes = 4),
            member.copy(rowMajor = true),
            member.copy(type = matrix),
            member.copy(type = array),
            member.copy(type = nestedArray, arrayStrideBytes = 4),
        )
        for (invalid in invalidMembers) {
            val resource = ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "params", null, null, emptyList(), Long.MAX_VALUE, "Params", listOf(invalid), null)
            assertFailsWith<IllegalArgumentException> { artifact(resources = listOf(resource)).encode() }
        }
        val image = ShaderImageDescription(ShaderImageDimension.TwoDimensional, false, false, false, scalar)
        val invalidResources = listOf(
            ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "params", null, null, emptyList(), -2, "Params", emptyList(), null),
            ShaderInterfaceResource(ShaderInterfaceResourceKind.UniformBuffer, "params", 0, 0, emptyList(), null, "Params", emptyList(), null),
            ShaderInterfaceResource(ShaderInterfaceResourceKind.StorageBuffer, "params", 0, 0, emptyList(), 4, null, listOf(member), null),
            ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "params", null, null, emptyList(), 0, "Params", listOf(member), null),
            ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "params", null, null, emptyList(), 4, "Params", listOf(member), image),
            ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 0, 0, emptyList(), 4, null, emptyList(), image),
            ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 0, 0, emptyList(), null, "Params", emptyList(), image),
            ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 0, 0, emptyList(), null, null, listOf(member), image),
            ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 0, 0, emptyList(), null, null, emptyList(), null),
        )
        for (resource in invalidResources) {
            assertFailsWith<IllegalArgumentException> { artifact(resources = listOf(resource)).encode() }
        }
    }

    @Test
    fun optionalSentinelsAndUnknownDescriptorExtentsKeepTheirWireMeaning() {
        val scalar = ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1)
        val image = ShaderImageDescription(ShaderImageDimension.TwoDimensional, false, false, false, scalar)
        val unknown = listOf(-1, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        val supplied = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", -1, -1, unknown, -1, null, emptyList(), image)
        val canonical = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", null, null, unknown.map { if (it == -1) null else it }, null, null, emptyList(), image)
        val encoded = artifact(listOf(ShaderInterfaceVariable("input", -1, scalar)), listOf(supplied)).encode()
        assertContentEquals(artifact(listOf(ShaderInterfaceVariable("input", null, scalar)), listOf(canonical)).encode(), encoded)
        val decoded = ShaderInterfaceArtifact.decode(ByteBuffer.wrap(encoded))
        assertNull(decoded.description.inputs.single().location)
        assertNull(decoded.description.resources.single().descriptorCount)
        assertNull(decoded.description.resources.single().set)
        assertNull(decoded.description.resources.single().binding)
        assertNull(decoded.description.resources.single().sizeBytes)
        assertContentEquals(encoded, decoded.encode())
        for (unknownDimension in listOf<Int?>(0, null, -1)) {
            val resource = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 0, 0, listOf(Int.MAX_VALUE, unknownDimension, Int.MAX_VALUE, Int.MAX_VALUE), null, null, emptyList(), image)
            assertNull(ShaderInterfaceArtifact.decode(ByteBuffer.wrap(artifact(resources = listOf(resource)).encode())).description.resources.single().descriptorCount)
        }
    }

    @Test
    fun blockImageMatrixAndArrayFactsSurviveRoundTripWithoutEncoderReadback() {
        val scalar = ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1)
        val matrix = ShaderInterfaceBlockMember("matrix", 0, 64, ShaderValueDescription(ShaderScalarKind.Float, 32, 4, 4), 16, 0, true)
        val array = ShaderInterfaceBlockMember("array", 64, 32, ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1, listOf(2)), 0, 16, false)
        val blocks = listOf(ShaderInterfaceResourceKind.UniformBuffer, ShaderInterfaceResourceKind.StorageBuffer, ShaderInterfaceResourceKind.PushConstant).map { kind ->
            ShaderInterfaceResource(kind, kind.name, 1, 2, listOf(2), 96, "Params", listOf(matrix, array), null)
        }
        val image = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 2, 3, listOf(0), null, null, emptyList(), ShaderImageDescription(ShaderImageDimension.Cube, true, true, true, scalar))
        val encoded = artifact(resources = blocks + image).encode()
        val decoded = ShaderInterfaceArtifact.decode(ByteBuffer.wrap(encoded))
        assertContentEquals(encoded, decoded.encode())
        for ((expected, actual) in blocks.zip(decoded.description.resources.take(3))) {
            assertEquals(expected.kind, actual.kind)
            assertEquals(expected.sizeBytes, actual.sizeBytes)
            assertEquals(2L, actual.descriptorCount)
            assertEquals(listOf(0, 64), actual.members.map { it.offsetBytes })
            assertEquals(listOf(16, 0), actual.members.map { it.matrixStrideBytes })
            assertEquals(listOf(0, 16), actual.members.map { it.arrayStrideBytes })
            assertEquals(listOf(true, false), actual.members.map { it.rowMajor })
        }
        assertNull(decoded.description.resources.last().descriptorCount)
        val decodedImage = assertNotNull(decoded.description.resources.last().image)
        assertEquals(ShaderImageDimension.Cube, decodedImage.dimension)
        assertTrue(decodedImage.depth && decodedImage.arrayed && decodedImage.multisampled)
        assertEquals(ShaderScalarKind.Float, decodedImage.sampledType.scalar)
        assertEquals(32, decodedImage.sampledType.bitWidth)
        assertEquals(1, decodedImage.sampledType.components)
        assertEquals(1, decodedImage.sampledType.columns)
    }

    @Test
    fun unknownNumericTagsAndExcessiveCountsAreRejected() {
        val valid = artifact(listOf(ShaderInterfaceVariable("", 0, ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1)))).encode()
        val input = ByteBuffer.wrap(valid)
        input.position(12); val textLength = input.int; input.position(input.position() + textLength + 32)
        val countOffset = input.position(); input.int; val nameLength = input.int; input.position(input.position() + nameLength + 4)
        val scalarOffset = input.position()
        for ([offset, value] in listOf(countOffset to Int.MAX_VALUE, scalarOffset to 999)) {
            val invalid = valid.copyOf(); ByteBuffer.wrap(invalid).putInt(offset, value)
            assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact.decode(ByteBuffer.wrap(invalid)) }
        }
    }

    @Test
    fun independentImageFixturePreservesSentinelsAndTheInputBufferWindow() {
        val dimensions = listOf(-1, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        val wire = imageArtifactBytes(dimensions)
        val scalar = ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1)
        val image = ShaderImageDescription(ShaderImageDimension.TwoDimensional, false, false, false, scalar)
        val resource = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", -1, -1, dimensions, -1, null, emptyList(), image)
        assertContentEquals(wire, artifact(resources = listOf(resource)).encode())

        val buffer = ByteBuffer.wrap(byteArrayOf(7, 8, 9) + wire + byteArrayOf(10)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(3)
        buffer.limit(3 + wire.size)
        val input = buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val decoded = ShaderInterfaceArtifact.decode(input)
        assertEquals(3, input.position())
        assertEquals(3 + wire.size, input.limit())
        assertEquals(ByteOrder.LITTLE_ENDIAN, input.order())
        val actual = decoded.description.resources.single()
        assertEquals(listOf(null, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE), actual.arrayDimensions)
        assertNull(actual.set)
        assertNull(actual.binding)
        assertNull(actual.sizeBytes)
        assertNull(actual.descriptorCount)
    }

    @Test
    fun descriptorOverflowKeepsItsOrderRelativeToUnknownDimensionsAndResourceFacts() {
        val scalar = ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1)
        val image = ShaderImageDescription(ShaderImageDimension.TwoDimensional, false, false, false, scalar)
        for (unknown in listOf<Int?>(0, null, -1)) {
            val earlyUnknown = listOf(Int.MAX_VALUE, unknown, Int.MAX_VALUE, Int.MAX_VALUE)
            val decoded = ShaderInterfaceArtifact.decode(ByteBuffer.wrap(imageArtifactBytes(earlyUnknown)))
            assertNull(decoded.description.resources.single().descriptorCount)

            val lateUnknown = listOf(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, unknown)
            val resource = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 0, 0, lateUnknown, null, null, emptyList(), image)
            val encodeFailure = assertFailsWith<IllegalArgumentException> { artifact(resources = listOf(resource)).encode() }
            val decodeFailure = assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact.decode(ByteBuffer.wrap(imageArtifactBytes(lateUnknown))) }
            for (failure in listOf(encodeFailure, decodeFailure)) {
                assertEquals("Descriptor count exceeds Long capacity", failure.message)
                assertIs<ArithmeticException>(failure.cause)
            }

            val invalidImage = ShaderInterfaceResource(resource.kind, resource.name, resource.set, resource.binding, lateUnknown, null, null, emptyList(), null)
            val invalidEncode = assertFailsWith<IllegalArgumentException> { artifact(resources = listOf(invalidImage)).encode() }
            assertEquals("Descriptor count exceeds Long capacity", invalidEncode.message)
            val invalidDecode = assertFailsWith<IllegalArgumentException> { ShaderInterfaceArtifact.decode(ByteBuffer.wrap(imageArtifactBytes(lateUnknown, includeImage = false))) }
            assertEquals("Invalid image resource facts", invalidDecode.message)
            assertNull(invalidDecode.cause)
        }
    }

    private fun imageArtifactBytes(dimensions: List<Int?>, includeImage: Boolean = true): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(0x52484946)
            output.writeInt(1)
            output.writeInt(2)
            output.writeInt(4)
            output.write(byteArrayOf(0x6d, 0x61, 0x69, 0x6e))
            output.write(ByteArray(32))
            output.writeInt(0)
            output.writeInt(0)
            output.writeInt(1)
            output.writeInt(4)
            output.writeInt(5)
            output.write(byteArrayOf(0x69, 0x6d, 0x61, 0x67, 0x65))
            output.writeInt(-1)
            output.writeInt(-1)
            output.writeInt(dimensions.size)
            for (dimension in dimensions) output.writeInt(dimension ?: -1)
            output.writeLong(-1)
            output.writeBoolean(false)
            output.writeInt(0)
            output.writeBoolean(includeImage)
            if (includeImage) {
                output.writeInt(2)
                output.writeBoolean(false)
                output.writeBoolean(false)
                output.writeBoolean(false)
                output.writeInt(4)
                output.writeInt(32)
                output.writeInt(1)
                output.writeInt(1)
                output.writeInt(0)
            }
        }
        return bytes.toByteArray()
    }
}
