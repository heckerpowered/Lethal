/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.prepare

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.geometry.UploadData
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import kotlin.test.*

class PassPreparationTest {
    @Test
    fun sameNumericObjectReusesOneAllocationAndUploadWithinPass() = withFixture { preparation ->
        val value = NumericParameterValue.floats(2f, 3f)
        val first = preparation.uniform(value)
        assertSame(first, preparation.uniform(value))
        assertEquals(1, created)
        assertEquals(1, preparation.snapshot().size)
    }

    @Test
    fun equalBytesInDistinctNumericObjectsRemainIndependent() = withFixture { preparation ->
        val first = preparation.uniform(NumericParameterValue.floats(2f, 3f))
        val second = preparation.uniform(NumericParameterValue.floats(2f, 3f))
        assertNotSame(first, second)
        assertEquals(2, created)
        assertEquals(2, preparation.snapshot().size)
    }

    @Test
    fun numericIdentityDoesNotShareAllocationsBetweenPasses() = withFixture { preparation ->
        val value = NumericParameterValue.floats(2f)
        val other = PassPreparation(device, preparation.lifetime)
        assertNotSame(preparation.uniform(value), other.uniform(value))
        assertEquals(2, created)
        assertEquals(1, preparation.snapshot().size)
        assertEquals(1, other.snapshot().size)
    }

    @Test
    fun uploadRolesStayIndependentAndReuseTheirOwnIdentity() = withFixture { preparation ->
        val bytes = UploadData(byteArrayOf(1, 2, 3, 4))
        val vertex = preparation.upload(bytes, BufferUsage.Vertex)
        val uniform = preparation.upload(bytes, BufferUsage.Uniform)
        assertNotSame(vertex, uniform)
        assertSame(vertex, preparation.upload(bytes, BufferUsage.Vertex))
        assertSame(uniform, preparation.upload(bytes, BufferUsage.Uniform))
        assertEquals(2, created)
        assertEquals(2, preparation.snapshot().size)
    }

    @Test
    fun failedAllocationIsNotCachedAndSuccessfulRetryBelongsToLifetime() = withFixture { preparation ->
        val value = NumericParameterValue.floats(2f)
        failNextAllocation = true
        assertFailsWith<IllegalStateException> { preparation.uniform(value) }
        assertEquals(0, created)
        assertTrue(preparation.snapshot().isEmpty())
        val ready = preparation.uniform(value)
        assertSame(ready, preparation.uniform(value))
        assertEquals(2, attempts)
        assertEquals(1, created)
        assertEquals(1, preparation.snapshot().size)
    }

    private class Fixture {
        var attempts = 0
        var created = 0
        var closed = 0
        var failNextAllocation = false
        val device = Proxy.newProxyInstance(GraphicsDevice::class.java.classLoader, arrayOf(GraphicsDevice::class.java)) { _, method, arguments ->
            check(method.name == "createBuffer")
            attempts++
            if (failNextAllocation) {
                failNextAllocation = false
                error("Allocation rejected by fixture")
            }
            val description = arguments[0] as BufferDescription
            created++
            Proxy.newProxyInstance(GpuBuffer::class.java.classLoader, arrayOf(GpuBuffer::class.java)) { _, property, _ ->
                when (property.name) {
                    "getSizeBytes" -> description.sizeBytes
                    "getUsage" -> description.usage
                    "close" -> terminateOnFailure { closed++; Unit }
                    else -> error("Unexpected buffer operation: ${property.name}")
                }
            } as GpuBuffer
        } as GraphicsDevice
    }

    private fun withFixture(block: Fixture.(PassPreparation) -> Unit) {
        val fixture = Fixture()
        ResourceLifetime.build {
            try {
                fixture.block(PassPreparation(fixture.device, this))
            } finally {
                close()
            }
        }
        assertEquals(fixture.created, fixture.closed)
    }
}
