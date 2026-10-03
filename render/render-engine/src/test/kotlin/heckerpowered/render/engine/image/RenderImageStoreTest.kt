/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.image

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.engine.image.ImageSize
import heckerpowered.render.engine.image.RenderImageStore
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.GpuTexture
import heckerpowered.render.resource.texture.GpuTextureView
import heckerpowered.render.resource.texture.TextureDescription
import heckerpowered.render.resource.texture.TextureDimension
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.resource.texture.TextureStorage
import heckerpowered.render.resource.texture.TextureUsage
import heckerpowered.render.resource.texture.createTexture
import java.lang.reflect.Proxy
import kotlin.test.*

class RenderImageStoreTest {
    @Test
    fun textureConvenienceOverloadForwardsDescriptionDefaultsAndValidatesBeforeAllocation() {
        val descriptions = mutableListOf<TextureDescription>()
        val texture = proxy<GpuTexture> { _, _ -> error("Unexpected texture read") }
        val device = proxy<GraphicsDevice> { method, arguments ->
            check(method == "createTexture")
            descriptions += arguments.single() as TextureDescription
            texture
        }
        val format = TextureFormat.Rgba8UnsignedNormalized
        assertSame(texture, device.createTexture("color", 16, 32, format, TextureUsage.Sampled, TextureUsage.ColorAttachment, TextureUsage.Sampled))
        val description = descriptions.single()
        assertEquals("color", description.label)
        assertEquals(16, description.width)
        assertEquals(32, description.height)
        assertEquals(format, description.format)
        assertEquals(setOf(TextureUsage.Sampled, TextureUsage.ColorAttachment), description.usage)
        assertEquals(TextureDimension.TwoDimensional, description.dimension)
        assertEquals(1, description.depth)
        assertEquals(1, description.mipLevelCount)
        assertEquals(1, description.arrayLayerCount)
        assertEquals(SampleCount.One, description.sampleCount)
        assertEquals(TextureStorage.Backed, description.storage)
        assertFalse(description.cubeCompatible)
        assertFailsWith<IllegalArgumentException> { device.createTexture("invalid", 0, 32, format, TextureUsage.Sampled) }
        assertFailsWith<IllegalArgumentException> { device.createTexture("invalid", 16, 32, format) }
        assertEquals(1, descriptions.size)
    }

    @Test
    fun failedImageCreationClosesNewResourcesAndKeepsThePreviousImage() {
        listOf("createTexture", "createTextureView", "createAttachmentView").forEach { operation ->
            val fixture = ImageStoreFixture()
            val size = ImageSize(16, 16)
            val initial = fixture.store.image("color", size, TextureFormat.Rgba8UnsignedNormalized)
            val failure = AssertionError("$operation failed")
            fixture.failures[operation] = failure

            assertSame(
                failure,
                assertFailsWith<AssertionError> {
                    fixture.store.image("color", ImageSize(32, 32), TextureFormat.Rgba8UnsignedNormalized)
                },
            )
            assertEquals(if (operation == "createTexture") emptyList() else listOf(2), fixture.closedTextures)
            assertSame(initial, fixture.store.image("color", size, TextureFormat.Rgba8UnsignedNormalized))
            fixture.store.releaseRetired()
            assertFalse(1 in fixture.closedTextures)

            fixture.failures.clear()
            fixture.store.image("color", ImageSize(32, 32), TextureFormat.Rgba8UnsignedNormalized)
            fixture.store.releaseRetired()
            assertEquals(1, fixture.closedTextures.count { it == 1 })
            fixture.lifetime.close()
            assertEquals(fixture.allocations, fixture.closedTextures.size)
        }
    }

    @Test
    fun failedFirstCacheInsertionRemovesThePartiallyInsertedEntry() {
        val fixture = ImageStoreFixture()
        val failure = AssertionError("cache insertion failed")
        var failInsertion = true
        val images = object : LinkedHashMap<String, Any>() {
            override fun put(key: String, value: Any): Any? {
                val previous = super.put(key, value)
                if (failInsertion) throw failure
                return previous
            }
        }
        replaceCollection(fixture.store, "images", images)

        assertSame(
            failure,
            assertFailsWith<AssertionError> {
                fixture.store.image("color", ImageSize(16, 16), TextureFormat.Rgba8UnsignedNormalized)
            },
        )
        assertTrue(images.isEmpty())
        assertEquals(listOf(1), fixture.closedTextures)

        failInsertion = false
        fixture.store.image("color", ImageSize(16, 16), TextureFormat.Rgba8UnsignedNormalized)
        fixture.lifetime.close()
        assertEquals(listOf(1, 2), fixture.closedTextures)
    }

    @Test
    fun failedRetirementRemovesThePreviousImageFromTheRetiredList() {
        val fixture = ImageStoreFixture()
        val size = ImageSize(16, 16)
        val initial = fixture.store.image("color", size, TextureFormat.Rgba8UnsignedNormalized)
        val failure = AssertionError("retirement failed")
        val retired = object : ArrayList<Any>() {
            override fun add(element: Any): Boolean {
                super.add(element)
                throw failure
            }
        }
        replaceCollection(fixture.store, "retired", retired)

        assertSame(
            failure,
            assertFailsWith<AssertionError> {
                fixture.store.image("color", ImageSize(32, 32), TextureFormat.Rgba8UnsignedNormalized)
            },
        )
        assertTrue(retired.isEmpty())
        assertEquals(listOf(2), fixture.closedTextures)
        assertSame(initial, fixture.store.image("color", size, TextureFormat.Rgba8UnsignedNormalized))
        fixture.store.releaseRetired()
        assertEquals(listOf(2), fixture.closedTextures)
        fixture.lifetime.close()
        assertEquals(listOf(2, 1), fixture.closedTextures)
    }

    @Test
    fun resizeRetiresPreviousTextureUntilCompletionAndCacheCannotOutliveLifetime() {
        var allocations = 0
        var closures = 0
        val device = proxy<GraphicsDevice> { method, arguments -> when (method) {
            "createTexture" -> {
                allocations++
                val description = arguments.single() as TextureDescription
                assertEquals("color", description.label)
                assertEquals(if (allocations == 1) 16 else 32, description.width)
                assertEquals(description.width, description.height)
                assertEquals(TextureFormat.Rgba8UnsignedNormalized, description.format)
                assertEquals(setOf(TextureUsage.Sampled, TextureUsage.ColorAttachment), description.usage)
                proxy<GpuTexture> { operation, _ ->
                    check(operation == "close")
                    closures++
                    null
                }
            }
            "createTextureView" -> proxy<GpuTextureView> { operation, _ ->
                check(operation == "getTexture")
                arguments[0]
            }
            "createAttachmentView" -> proxy<RenderAttachment> { _, _ -> error("Unexpected attachment read") }
            else -> error("Unexpected operation $method")
        } }
        val lifetime = ResourceLifetime.build { this }
        val store = RenderImageStore(device, lifetime, proxy<GpuSampler> { _, _ -> error("Unexpected sampler read") })
        val initial = store.image("color", ImageSize(16, 16), TextureFormat.Rgba8UnsignedNormalized)
        assertSame(initial, store.image("color", ImageSize(16, 16), TextureFormat.Rgba8UnsignedNormalized))
        store.image("color", ImageSize(32, 32), TextureFormat.Rgba8UnsignedNormalized)
        assertEquals(2, allocations)
        assertEquals(0, closures)
        store.releaseRetired()
        assertEquals(1, closures)
        lifetime.close()
        assertEquals(2, closures)
        assertFailsWith<IllegalStateException> { store.image("color", ImageSize(32, 32), TextureFormat.Rgba8UnsignedNormalized) }
    }

    @Test
    fun rootClosureReleasesCurrentAndRetiredImagesOnceAndRejectsCacheRequests() {
        val fixture = ImageStoreFixture()
        val size = ImageSize(16, 16)
        val initial = fixture.store.image("color", size, TextureFormat.Rgba8UnsignedNormalized)
        assertSame(initial, fixture.store.image("color", size, TextureFormat.Rgba8UnsignedNormalized))
        fixture.store.image("color", ImageSize(32, 32), TextureFormat.Rgba8UnsignedNormalized)
        assertTrue(fixture.closedTextures.isEmpty())

        fixture.lifetime.close()
        fixture.lifetime.close()
        assertEquals(listOf(1, 2), fixture.closedTextures)
        assertFailsWith<IllegalStateException> { fixture.store.image("color", ImageSize(32, 32), TextureFormat.Rgba8UnsignedNormalized) }
        assertFailsWith<IllegalStateException> { fixture.store.image("new", size, TextureFormat.Rgba8UnsignedNormalized) }
        assertEquals(2, fixture.allocations)
    }

    @Test
    fun rootConstructionFailureReleasesAutomaticallyRegisteredImages() {
        val fixture = ImageStoreFixture()
        val failure = AssertionError("root construction failed")
        assertSame(
            failure,
            assertFailsWith<AssertionError> {
                ResourceLifetime.build {
                    val store = RenderImageStore(
                        fixture.device,
                        this,
                        proxy<GpuSampler> { _, _ -> error("Unexpected sampler read") },
                    )
                    store.image("color", ImageSize(16, 16), TextureFormat.Rgba8UnsignedNormalized)
                    store.image("color", ImageSize(32, 32), TextureFormat.Rgba8UnsignedNormalized)
                    throw failure
                }
            },
        )
        assertEquals(listOf(1, 2), fixture.closedTextures)
        fixture.lifetime.close()
        assertEquals(listOf(1, 2), fixture.closedTextures)
    }

    @Test
    fun closedRootRejectsStoreConstructionBeforeNativeAllocation() {
        val fixture = ImageStoreFixture()
        fixture.lifetime.close()
        assertFailsWith<IllegalStateException> {
            RenderImageStore(
                fixture.device,
                fixture.lifetime,
                proxy<GpuSampler> { _, _ -> error("Unexpected sampler read") },
            )
        }
        assertEquals(0, fixture.allocations)
        assertTrue(fixture.closedTextures.isEmpty())
    }

    private inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments ->
            invoke(method.name, arguments ?: emptyArray())
        } as T

    // Inject collection failures at the handoff boundary without adding a production fault-injection API.
    private fun replaceCollection(store: RenderImageStore, name: String, collection: Any) {
        val field = RenderImageStore::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(store, collection)
    }

    private inner class ImageStoreFixture {
        var allocations = 0
        val closedTextures = mutableListOf<Int>()
        val failures = mutableMapOf<String, AssertionError>()
        val lifetime = ResourceLifetime.build { this }
        val device = proxy<GraphicsDevice> { method, arguments ->
            failures[method]?.let { throw it }
            when (method) {
                "createTexture" -> {
                    val identifier = ++allocations
                    proxy<GpuTexture> { operation, _ ->
                        check(operation == "close")
                        closedTextures += identifier
                        null
                    }
                }
                "createTextureView" -> proxy<GpuTextureView> { operation, _ ->
                    check(operation == "getTexture")
                    arguments[0]
                }
                "createAttachmentView" -> proxy<RenderAttachment> { _, _ -> error("Unexpected attachment read") }
                else -> error("Unexpected operation $method")
            }
        }
        val store = RenderImageStore(device, lifetime, proxy<GpuSampler> { _, _ -> error("Unexpected sampler read") })
    }
}
