/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.*
import heckerpowered.render.opengl.function.*
import heckerpowered.render.opengl.shader.*
import heckerpowered.render.pipeline.*
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.resource.sampler.*
import heckerpowered.render.resource.texture.*
import heckerpowered.render.resource.buffer.*
import java.nio.IntBuffer
import heckerpowered.render.shader.*
import heckerpowered.render.shader.binding.*
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.*

class OpenGLDescriptorTest {
    @Test
    fun sparseSetsArraysAndMissingLocationsUseDistinctUnitsAndRestoreAllHostState() {
        for (objects in listOf(false, true)) DescriptorFixture(objects).use { fixture ->
            val source = fixture.image()
            val sampler = fixture.sampler()
            val layout = DescriptorSetLayout(listOf(DescriptorBindingLayout(
                9,
                DescriptorType.CombinedTextureSampler(),
                setOf(ShaderStage.Fragment),
                3,
            )))
            val selection = fixture.selection(layout, List(3) { source to sampler })
            val pipeline = fixture.pipeline(listOf(layout, DescriptorSetLayout.Empty, layout), listOf(
                fixture.binding(0, 9, 3, listOf(UniformLocation(4), UniformLocation.Missing, UniformLocation(6))),
                fixture.binding(2, 9, 3, listOf(UniformLocation(7), UniformLocation(8))),
            ))
            val before = fixture.driver.snapshot()
            fixture.pass(listOf(selection)) {
                bindDescriptorSet(2, selection)
                bindDescriptorSet(0, selection)
                bindPipeline(pipeline)
                draw(3)
            }
            assertEquals(listOf(4 to 0, 6 to 2, 7 to 3, 8 to 4), fixture.driver.uniforms)
            assertEquals(before, fixture.driver.snapshot())
            assertEquals(1, fixture.driver.drawStates.size)
            assertEquals(setOf(0, 2, 3, 4), fixture.driver.sampledUnits.toSet())
        }
    }

    @Test
    fun logicalSelectionSurvivesPipelineSwitchButResetsForTheNextPass() {
        DescriptorFixture().use { fixture ->
            val source = fixture.image()
            val firstLayout = fixture.layout(1)
            val secondLayout = fixture.layout(2)
            val selection = fixture.selection(firstLayout, listOf(source to fixture.sampler()))
            val first = fixture.pipeline(listOf(firstLayout), listOf(fixture.binding(0, 1)))
            val equivalent = fixture.pipeline(listOf(fixture.layout(1)), listOf(fixture.binding(0, 1, locations = listOf(UniformLocation(12)))))
            val incompatible = fixture.pipeline(listOf(secondLayout), listOf(fixture.binding(0, 2)))
            fixture.pass(listOf(selection)) {
                bindDescriptorSet(0, selection)
                bindPipeline(first)
                draw(3)
                bindPipeline(incompatible)
                assertFailsWith<IllegalArgumentException> { draw(3) }
                bindPipeline(equivalent)
                draw(3)
                bindPipeline(first)
                draw(3)
            }
            assertEquals(listOf(4 to 0, 12 to 0, 4 to 0), fixture.driver.uniforms)
            fixture.pass(listOf(selection)) {
                bindPipeline(first)
                assertFailsWith<IllegalStateException> { draw(0) }
            }
            assertEquals(3, fixture.driver.drawStates.size)
        }
    }

    @Test
    fun undeclaredImagesForeignAndClosedResourcesRejectBeforeNativeMutations() {
        DescriptorFixture().use { fixture ->
            val layout = fixture.layout()
            val source = fixture.image()
            val sampler = fixture.sampler()
            val selection = fixture.selection(layout, listOf(source to sampler))
            fixture.pass(emptyList()) {
                val before = fixture.driver.snapshot()
                assertFailsWith<IllegalArgumentException> { bindDescriptorSet(0, selection) }
                assertEquals(before, fixture.driver.snapshot())
            }
            source.texture.close()
            val before = fixture.driver.snapshot()
            assertFailsWith<IllegalStateException> { fixture.pass(listOf(selection)) {} }
            assertEquals(before, fixture.driver.snapshot())
            DescriptorFixture().use { other ->
                val foreign = fixture.selection(layout, listOf(other.image() to sampler))
                assertFailsWith<IllegalArgumentException> { fixture.pass(listOf(foreign)) {} }
                val wrongSampler = fixture.selection(layout, listOf(fixture.image() to other.sampler()))
                assertFailsWith<IllegalArgumentException> { fixture.pass(listOf(wrongSampler)) {} }
            }
            val closed = fixture.sampler().also { it.close() }
            val invalid = fixture.selection(layout, listOf(fixture.image() to closed))
            assertFailsWith<IllegalStateException> { fixture.pass(listOf(invalid)) {} }
        }
    }

    @Test
    fun fullDeclarationsRejectAttachmentAliasesAndUnsupportedKindsBeforeClear() {
        DescriptorFixture().use { fixture ->
            val alias = fixture.selection(fixture.layout(), listOf(fixture.targetView to fixture.sampler()))
            val before = fixture.driver.snapshot()
            assertFailsWith<IllegalArgumentException> { fixture.pass(listOf(alias)) {} }
            assertEquals(before, fixture.driver.snapshot())
            val unsupported = DescriptorSet(
                DescriptorSetLayout(listOf(DescriptorBindingLayout(
                    0,
                    DescriptorType.Sampler,
                    setOf(ShaderStage.Fragment),
                ))),
                listOf(DescriptorBinding(
                    0,
                    listOf(DescriptorResource.Sampler(fixture.sampler())),
                )),
            )
            assertFailsWith<UnsupportedOperationException> { fixture.pass(listOf(unsupported)) {} }
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun incompatibleFallbackSamplersRejectBeforeAnyTextureOrUniformMutation() {
        DescriptorFixture(false).use { fixture ->
            val image = fixture.image()
            val layout = fixture.layout(count = 2)
            val selection = fixture.selection(layout, listOf(image to fixture.sampler(), image to fixture.sampler(TextureFilter.Linear)))
            val pipeline = fixture.pipeline(listOf(layout), listOf(fixture.binding(0, 0, 2, listOf(UniformLocation(4), UniformLocation(5)))))
            fixture.pass(listOf(selection)) {
                bindPipeline(pipeline)
                bindDescriptorSet(0, selection)
                val before = fixture.driver.snapshot()
                val mutations = fixture.driver.mutations.toList()
                assertFailsWith<UnsupportedOperationException> { draw(3) }
                assertEquals(before, fixture.driver.snapshot())
                assertEquals(mutations, fixture.driver.mutations)
                assertTrue(fixture.driver.uniforms.isEmpty())
            }
        }
        DescriptorFixture(true).use { fixture ->
            val image = fixture.image()
            val layout = fixture.layout(count = 2)
            val selection = fixture.selection(layout, listOf(image to fixture.sampler(), image to fixture.sampler(TextureFilter.Linear)))
            val pipeline = fixture.pipeline(listOf(layout), listOf(fixture.binding(0, 0, 2, listOf(UniformLocation(4), UniformLocation(5)))))
            fixture.pass(listOf(selection)) { bindPipeline(pipeline); bindDescriptorSet(0, selection); draw(3) }
            assertEquals(1, fixture.driver.drawStates.size)
        }
    }

    @Test
    fun importedPhysicalAliasesRejectFeedbackAndGroupFallbackSampling() {
        for (objects in listOf(false, true)) {
            DescriptorFixture(objects).use { fixture ->
                val attachmentAlias = fixture.imported(fixture.targetView)
                val feedback = fixture.selection(fixture.layout(), listOf(attachmentAlias to fixture.sampler()))
                val beforeFeedback = fixture.driver.snapshot()
                assertFailsWith<IllegalArgumentException> { fixture.pass(listOf(feedback)) {} }
                assertEquals(beforeFeedback, fixture.driver.snapshot())

                val image = fixture.image()
                val first = fixture.imported(image)
                val second = fixture.imported(image)
                val layout = fixture.layout(count = 2)
                val selection = fixture.selection(layout, listOf(first to fixture.sampler(), second to fixture.sampler(TextureFilter.Linear)))
                val pipeline = fixture.pipeline(listOf(layout), listOf(fixture.binding(0, 0, 2, listOf(UniformLocation(4), UniformLocation(5)))))
                val beforePass = fixture.driver.snapshot()
                fixture.pass(listOf(selection)) {
                    bindPipeline(pipeline)
                    bindDescriptorSet(0, selection)
                    val beforeDraw = fixture.driver.snapshot()
                    val mutations = fixture.driver.mutations.toList()
                    if (objects) {
                        draw(3)
                    } else {
                        assertFailsWith<UnsupportedOperationException> { draw(3) }
                        assertEquals(beforeDraw, fixture.driver.snapshot())
                        assertEquals(mutations, fixture.driver.mutations)
                        assertTrue(fixture.driver.uniforms.isEmpty())
                    }
                }
                assertEquals(beforePass, fixture.driver.snapshot())
                assertEquals(if (objects) 1 else 0, fixture.driver.drawStates.size)
            }
        }
    }

    @Test
    fun failedQueriesSelectionsAndDrawsRestoreTouchedAndUntouchedUnitsAndInvalidateEncoder() {
        for (objects in listOf(false, true)) for (failure in listOf("activeTexture", "getTextureParameter", "textureParameter", "uniformInt", "drawArrays") + if (objects) listOf("bindSampler") else listOf("getTextureUnitLodBias", "textureUnitLodBias")) {
            DescriptorFixture(objects).use { fixture ->
                val image = fixture.image()
                val selection = fixture.selection(fixture.layout(), listOf(image to fixture.sampler()))
                val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.binding(0, 0)))
                val before = fixture.driver.snapshot()
                assertFailsWith<IllegalStateException> {
                    fixture.pass(listOf(selection)) {
                        bindPipeline(pipeline)
                        bindDescriptorSet(0, selection)
                        fixture.driver.failOnce = failure
                        assertFailsWith<AssertionError> { draw(3) }
                        assertFailsWith<IllegalStateException> { draw(3) }
                    }
                }
                assertEquals(before, fixture.driver.snapshot(), "objects=$objects failure=$failure")
            }
        }
    }

    @Test
    fun secondUnitSelectionFailureCannotCorruptAnUncapturedHostUnit() {
        DescriptorFixture(false).use { fixture ->
            val layout = fixture.layout(count = 2)
            val selection = fixture.selection(layout, listOf(fixture.image() to fixture.sampler(), fixture.image() to fixture.sampler()))
            val pipeline = fixture.pipeline(listOf(layout), listOf(fixture.binding(0, 0, 2, listOf(UniformLocation(4), UniformLocation(5)))))
            val before = fixture.driver.snapshot()
            assertFailsWith<AssertionError> {
                fixture.pass(listOf(selection)) {
                    bindPipeline(pipeline)
                    bindDescriptorSet(0, selection)
                    fixture.driver.failOnUnit = 1
                    draw(3)
                }
            }
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun sharedImageUnitsStillCountPerStageAgainstCombinedLimit() {
        DescriptorFixture().use { fixture ->
            for (parameter in listOf(0x8B4D, 0x8B4C, 0x8872)) fixture.driver.limits[parameter] = 16
            fun declaration(count: Int) = DescriptorSetLayout(listOf(DescriptorBindingLayout(
                7,
                DescriptorType.CombinedTextureSampler(),
                setOf(ShaderStage.Vertex, ShaderStage.Fragment),
                count,
            )))
            assertFailsWith<UnsupportedOperationException> {
                fixture.device.createPipelineLayout(PipelineLayoutDescription(
                    descriptorSets = listOf(declaration(9)),
                    label = "18 stage accesses with 9 image units",
                ))
            }
            val layout = fixture.device.createPipelineLayout(PipelineLayoutDescription(
                descriptorSets = listOf(declaration(8)),
                label = "16 stage accesses with 8 image units",
            )) as OpenGLPipelineLayout
            layout.use { assertEquals(7, it.textureUnit(0, 7, 7).value) }
        }
    }

    @Test
    fun unusedWAddressingDoesNotConflictForTwoDimensionalFallback() {
        DescriptorFixture(false).use { fixture ->
            val image = fixture.image()
            val layout = fixture.layout(count = 2)
            val first = fixture.sampler()
            val second = fixture.sampler(addressW = SamplerAddressMode.Repeat)
            val selection = fixture.selection(layout, listOf(image to first, image to second))
            val pipeline = fixture.pipeline(listOf(layout), listOf(fixture.binding(0, 0, 2, listOf(UniformLocation(4), UniformLocation(5)))))
            val before = fixture.driver.snapshot()
            fixture.pass(listOf(selection)) { bindDescriptorSet(0, selection); bindPipeline(pipeline); draw(3) }
            assertEquals(1, fixture.driver.drawStates.size)
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun legacyUnitLodBiasIsNeutralDuringDrawAndRestoredWithOrWithoutSamplerObjects() {
        for (objects in listOf(false, true)) for (legacy in listOf(false, true)) DescriptorFixture(
            objects,
            legacy,
        ).use { fixture ->
            val image = fixture.image()
            val selection = fixture.selection(fixture.layout(), listOf(image to fixture.sampler()))
            val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.binding(0, 0)))
            val before = fixture.driver.snapshot()
            fixture.pass(listOf(selection)) { bindDescriptorSet(0, selection); bindPipeline(pipeline); draw(3) }
            assertEquals(if (legacy) 0f else 8f, fixture.driver.drawBias.single().getValue(0))
            assertEquals(before, fixture.driver.snapshot())
            assertEquals(legacy, "textureUnitLodBias" in fixture.driver.mutations)
        }
    }

    @Test
    fun legacyBiasNativeErrorsRestoreHostStateAndSealTheEncoder() {
        for (objects in listOf(false, true)) for (operation in listOf("getTextureUnitLodBias", "textureUnitLodBias")) DescriptorFixture(
            objects,
            true,
        ).use { fixture ->
            val selection = fixture.selection(fixture.layout(), listOf(fixture.image() to fixture.sampler()))
            val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.binding(0, 0)))
            val before = fixture.driver.snapshot()
            assertFailsWith<IllegalStateException> {
                fixture.pass(listOf(selection)) {
                    bindDescriptorSet(0, selection)
                    bindPipeline(pipeline)
                    fixture.driver.errorOnce = operation
                    val failure = assertFailsWith<OpenGLOperationException> { draw(3) }
                    assertEquals(0x0502, failure.errorCode)
                    assertFailsWith<IllegalStateException> { draw(3) }
                }
            }
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun referencedResourceDestructionAndRestorationFailuresHaltWithoutShutdownHooks() {
        for (kind in listOf("texture", "importtexture", "sampler", "restore")) {
            val marker = File.createTempFile("lethal-descriptor-$kind", ".marker")
            marker.delete()
            val process = ProcessBuilder(
                File(
                    System.getProperty("java.home"),
                    "bin/java",
                ).absolutePath,
                "-cp",
                System.getProperty("java.class.path"),
                "heckerpowered.render.opengl.OpenGLDescriptorFatalProbe",
                kind,
                marker.absolutePath,
            ).redirectErrorStream(true).start()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(process.exitValue() != 0, output)
            val expectedFailure = if (kind == "restore") "descriptor restore failed" else "active render pass"
            assertTrue(expectedFailure in output, output)
            assertFalse(marker.exists(), output)
        }
    }

    @Test
    fun limitQueryErrorsAreCreationFailuresAndDoNotTouchImageUnits() {
        DescriptorFixture().use { fixture ->
            val before = fixture.driver.snapshot()
            fixture.driver.queryError = 0x8B4D
            val failure = assertFailsWith<PipelineLayoutCreationException> {
                fixture.device.createPipelineLayout(PipelineLayoutDescription(
                    descriptorSets = listOf(fixture.layout()),
                    label = "query error",
                ))
            }
            assertEquals(0x0502, (failure.cause as OpenGLOperationException).errorCode)
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun uniformOnlyDrawBindsActualViewAndRestoresBaseGenericAndBlockMapping() {
        DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val selection = fixture.uniformSelection(fixture.uniformLayout(), listOf(view))
            val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.uniformBinding()))
            val before = fixture.driver.snapshot()
            val textureCalls = fixture.driver.mutations.toList()
            fixture.pass(listOf(selection)) {
                bindPipeline(pipeline)
                bindDescriptorSet(0, selection)
                draw(3)
            }
            assertEquals(listOf((view.buffer as OpenGLBuffer).name.value.toLong(), 256L, 17L), fixture.driver.uniformDraws.single().first.getValue(0))
            assertEquals(0, fixture.driver.uniformDraws.single().second.getValue(1 to 3))
            assertEquals(before, fixture.driver.snapshot())
            assertEquals(textureCalls, fixture.driver.mutations)
        }
    }

    @Test
    fun mixedSparseDescriptorsUseSeparateImageAndUniformAddressesAndPermitAliasedReads() {
        DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val uniformLayout = DescriptorSetLayout(listOf(
                DescriptorBindingLayout(
                    2,
                    DescriptorType.UniformBuffer(),
                    setOf(ShaderStage.Vertex),
                ),
                DescriptorBindingLayout(
                    9,
                    DescriptorType.UniformBuffer(),
                    setOf(ShaderStage.Fragment),
                ),
            ))
            val uniform = fixture.uniformSelection(uniformLayout, listOf(view, view))
            val sampled = fixture.selection(fixture.layout(5), listOf(fixture.image() to fixture.sampler()))
            val pipeline = fixture.pipeline(listOf(uniformLayout, DescriptorSetLayout.Empty, sampled.layout), listOf(
                fixture.uniformBinding(binding = 2, stages = setOf(ShaderStage.Vertex)),
                fixture.uniformBinding(binding = 9, block = 4),
                fixture.binding(2, 5),
            ))
            val before = fixture.driver.snapshot()
            fixture.pass(listOf(uniform, sampled)) {
                bindPipeline(pipeline)
                bindDescriptorSet(0, uniform)
                bindDescriptorSet(2, sampled)
                draw(3)
            }
            val state = fixture.driver.uniformDraws.single()
            assertEquals(state.first.getValue(0), state.first.getValue(1))
            assertEquals(0, state.second.getValue(1 to 3))
            assertEquals(1, state.second.getValue(1 to 4))
            assertEquals(listOf(4 to 0), fixture.driver.uniforms)
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun oneProgramAcrossLayoutsReassignsBlocksPerDrawWithoutCachingPipelineState() {
        DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val layout = fixture.uniformLayout()
            val selection = fixture.uniformSelection(layout, listOf(view))
            val active = fixture.uniformBinding(set = 1)
            val first = fixture.pipeline(listOf(DescriptorSetLayout.Empty, layout), listOf(active))
            val second = fixture.pipeline(listOf(fixture.uniformLayout(1), layout), listOf(active))
            val before = fixture.driver.snapshot()
            fixture.pass(listOf(selection)) {
                bindDescriptorSet(1, selection)
                for (pipeline in listOf(first, second, first)) {
                    bindPipeline(pipeline)
                    draw(3)
                }
            }
            assertEquals(listOf(0, 1, 0), fixture.driver.uniformDraws.map { it.second.getValue(1 to 3) })
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun uniformAlignmentAndNativeTailPaddingAreCheckedEvenAtZeroDraw() {
        for (offset in listOf(1L, 256L)) for (size in listOf(4L, 17L)) DescriptorFixture().use { fixture ->
            val view = fixture.uniformView(offset, size)
            val selection = fixture.uniformSelection(fixture.uniformLayout(), listOf(view))
            val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.uniformBinding(required = 16)))
            fixture.pass(listOf(selection)) {
                bindPipeline(pipeline)
                bindDescriptorSet(0, selection)
                for (count in listOf(0, 3)) {
                    if (offset == 256L && size == 17L) draw(count)
                    else assertFailsWith<IllegalArgumentException> { draw(count) }
                }
            }
            assertEquals(if (offset == 256L && size == 17L) 1 else 0, fixture.driver.uniformDraws.size)
            if (offset != 256L || size != 17L) assertTrue(fixture.driver.uniformCalls.isEmpty())
        }
    }

    @Test
    fun uniformDeclarationsAuthorizeIntervalsAndStagesRatherThanExactSelections() {
        DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            fun selection(offset: Long, size: Long, stages: Set<ShaderStage> = setOf(ShaderStage.Fragment)) = fixture.uniformSelection(
                fixture.uniformLayout(stages = stages),
                listOf(GpuBufferView(
                    view.buffer,
                    offset,
                    size,
                )),
            )
            val selected = selection(256, 17)
            val pipeline = fixture.pipeline(listOf(selected.layout), listOf(fixture.uniformBinding()))
            val declared = selection(1, 2047)
            fixture.pass(listOf(declared)) {
                bindPipeline(pipeline)
                bindDescriptorSet(0, selected)
                draw(3)
            }
            fixture.pass(listOf(selection(256, 8), selection(264, 9))) {
                bindPipeline(pipeline)
                bindDescriptorSet(0, selected)
                draw(3)
            }
            for (declarations in listOf(
                listOf(selection(256, 8), selection(265, 8)),
                listOf(selection(256, 17, setOf(ShaderStage.Vertex))),
            )) fixture.pass(declarations) {
                assertFailsWith<IllegalArgumentException> { bindDescriptorSet(0, selected) }
            }
            assertEquals(2, fixture.driver.uniformDraws.size)
        }
    }

    @Test
    fun zeroUniformDrawsValidateSelectionsAndAvoidHostCaptureAndBindings() {
        DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val selection = fixture.uniformSelection(fixture.uniformLayout(), listOf(view))
            val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.uniformBinding()))
            fixture.pass(listOf(selection), indexBuffers = listOf(view)) {
                bindPipeline(pipeline)
                assertFailsWith<IllegalStateException> { draw(0) }
                bindDescriptorSet(0, selection)
                bindIndexBuffer(view, heckerpowered.render.pipeline.primitive.IndexFormat.Uint8)
                draw(0)
                draw(3, instanceCount = 0)
                drawIndexed(0)
                drawIndexed(3, instanceCount = 0)
                assertFailsWith<UnsupportedOperationException> { draw(0, firstInstance = 1) }
                assertFailsWith<UnsupportedOperationException> { drawIndexed(0, firstInstance = 1) }
            }
            assertTrue(fixture.driver.uniformCalls.isEmpty())
            assertTrue(fixture.driver.base.calls.none { it == "createVAO" })
        }
    }

    @Test
    fun indexedUniformDrawUsesTheSameDescriptorConsumptionAndRestoration() {
        DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val selection = fixture.uniformSelection(fixture.uniformLayout(), listOf(view))
            val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.uniformBinding()))
            val before = fixture.driver.snapshot()
            fixture.pass(listOf(selection), indexBuffers = listOf(view)) {
                bindPipeline(pipeline)
                bindDescriptorSet(0, selection)
                bindIndexBuffer(view, heckerpowered.render.pipeline.primitive.IndexFormat.Uint8)
                drawIndexed(3)
            }
            assertEquals(1, fixture.driver.uniformDraws.size)
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun integerRangeFallbackRejectsOnlyAmbiguousTouchedRangesBeforeVaoMutation() {
        for (wide in listOf(false, true)) for (abi64 in listOf(false, true)) DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val selection = fixture.uniformSelection(fixture.uniformLayout(), listOf(view))
            val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.uniformBinding()))
            fixture.driver.range64 = wide
            fixture.driver.nativeSizeLimit = if (abi64) Long.MAX_VALUE else Int.MAX_VALUE.toLong()
            fixture.driver.uniformPoints[0] = listOf(70L, 256L, Int.MAX_VALUE.toLong())
            // An untouched point cannot downgrade this draw.
            fixture.driver.uniformPoints[7] = listOf(77L, 4294967296L, 32L)
            val before = fixture.driver.snapshot()
            fixture.pass(listOf(selection)) {
                bindPipeline(pipeline)
                bindDescriptorSet(0, selection)
                if (!wide && abi64) {
                    assertFailsWith<UnsupportedOperationException> { draw(3) }
                    draw(0)
                    assertTrue(fixture.driver.base.calls.none { it == "createVAO" })
                    assertTrue(fixture.driver.uniformCalls.none { it.startsWith("buffer:") || it.startsWith("block:") })
                } else draw(3)
            }
            assertEquals(before, fixture.driver.snapshot())
        }
        DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val selection = fixture.uniformSelection(fixture.uniformLayout(), listOf(view))
            val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.uniformBinding()))
            fixture.driver.uniformPoints[0] = listOf(70L, 4294967296L, 4294967313L)
            val before = fixture.driver.snapshot()
            fixture.pass(listOf(selection)) { bindPipeline(pipeline); bindDescriptorSet(0, selection); draw(3) }
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun smallIntegerQueriesRestoreBaseRangeAndUnboundPointsExactly() {
        DescriptorFixture().use { fixture ->
            fixture.driver.range64 = false
            val view = fixture.uniformView()
            val layout = DescriptorSetLayout((0..2).map { DescriptorBindingLayout(
                it,
                DescriptorType.UniformBuffer(),
                setOf(ShaderStage.Fragment),
            ) })
            val selection = fixture.uniformSelection(layout, listOf(view, view, view))
            fixture.driver.uniformBlocks[1 to 5] = 7
            val pipeline = fixture.pipeline(listOf(layout), (0..2).map { fixture.uniformBinding(binding = it, block = it + 3) })
            fixture.driver.uniformPoints[0] = listOf(Int.MIN_VALUE.toLong(), 0L, 0L)
            val before = fixture.driver.snapshot()
            fixture.pass(listOf(selection)) { bindPipeline(pipeline); bindDescriptorSet(0, selection); draw(3) }
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun secondPointCaptureAndApplyFailuresRestoreHostStateAndSealEncoder() {
        for (failure in listOf("query", "query-native", "alignment-native", "apply", "block", "native-range", "draw")) DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val layout = DescriptorSetLayout((0..1).map { DescriptorBindingLayout(
                it,
                DescriptorType.UniformBuffer(),
                setOf(ShaderStage.Fragment),
            ) })
            val selection = fixture.uniformSelection(layout, listOf(view, view))
            val pipeline = fixture.pipeline(listOf(layout), (0..1).map { fixture.uniformBinding(binding = it, block = it + 3) })
            val before = fixture.driver.snapshot()
            assertFailsWith<IllegalStateException> { fixture.pass(listOf(selection)) {
                bindPipeline(pipeline)
                bindDescriptorSet(0, selection)
                when (failure) {
                    "query" -> fixture.driver.failUniformQuery = 1
                    "query-native" -> fixture.driver.errorOnce = "getBoundUniformBuffer"
                    "alignment-native" -> fixture.driver.queryError = 0x8A34
                    "apply" -> fixture.driver.failUniformApply = 1
                    "block" -> fixture.driver.failOnce = "bindUniformBlock"
                    "native-range" -> fixture.driver.errorOnce = "bindUniformBuffer"
                    "draw" -> fixture.driver.failOnce = "drawArrays"
                }
                if (failure in listOf("native-range", "query-native", "alignment-native")) assertFailsWith<OpenGLOperationException> { draw(3) }
                else assertFailsWith<AssertionError> { draw(3) }
                assertFailsWith<IllegalStateException> { draw(0) }
                if (failure in listOf("query", "query-native", "alignment-native")) {
                    assertTrue(fixture.driver.uniformCalls.none { it.startsWith("buffer:") || it.startsWith("block:") })
                    assertTrue(fixture.driver.base.calls.none { it == "createVAO" })
                }
            }
            }
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun closedForeignUniformResourcesRejectBeforePassAndMinimumRemainsViewMeaning() {
        DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val selection = fixture.uniformSelection(fixture.uniformLayout(), listOf(view))
            view.buffer.close()
            var entered = false
            assertFailsWith<IllegalStateException> { fixture.pass(listOf(selection)) { entered = true } }
            assertFalse(entered)
        }
        DescriptorFixture().use { fixture -> DescriptorFixture().use { other ->
            val view = other.uniformView()
            val selection = fixture.uniformSelection(fixture.uniformLayout(), listOf(view))
            assertFailsWith<IllegalArgumentException> { fixture.pass(listOf(selection)) {} }
        } }
        DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val selection = fixture.uniformSelection(fixture.uniformLayout(), listOf(view))
            fixture.pass(emptyList(), indexBuffers = listOf(view)) {
                assertFailsWith<IllegalArgumentException> { bindDescriptorSet(0, selection) }
            }
            fixture.driver.nativeUniforms = false
            fixture.pass(listOf(selection)) {}
            fixture.driver.nativeUniforms = true
            val large = fixture.uniformView(size = 2048)
            val largeSelection = fixture.uniformSelection(fixture.uniformLayout(minimum = 2048), listOf(large))
            val pipeline = fixture.pipeline(listOf(largeSelection.layout), listOf(fixture.uniformBinding()))
            fixture.pass(listOf(largeSelection)) { bindPipeline(pipeline); bindDescriptorSet(0, largeSelection); draw(3) }
        }
    }

    @Test
    fun uniformReferenceAndRestorationFailuresHaltBeforeDeletionWithoutShutdownHooks() {
        for (kind in listOf("declared", "bound", "restore")) {
            val marker = File.createTempFile("lethal-uniform-$kind", ".marker").apply { delete() }
            val process = ProcessBuilder(
                File(
                    System.getProperty("java.home"),
                    "bin/java",
                ).absolutePath,
                "-cp",
                System.getProperty("java.class.path"),
                "heckerpowered.render.opengl.OpenGLUniformFatalProbe",
                kind,
                marker.absolutePath,
            ).redirectErrorStream(true).start()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(1, process.exitValue(), output)
            assertTrue(if (kind == "restore") "uniform restore failed" in output else "active render pass" in output, output)
            assertFalse("delete-referenced-buffer" in output, output)
            assertFalse(marker.exists(), output)
        }
    }

    @Test
    fun stageAndCombinedLimitsCountSparseFixedArraysBeforeOptimization() {
        for (parameter in listOf(0x8B4D, 0x8B4C, 0x8872)) DescriptorFixture().use { fixture ->
            fixture.driver.limits[parameter] = 1
            val layout = DescriptorSetLayout(listOf(DescriptorBindingLayout(
                7,
                DescriptorType.CombinedTextureSampler(),
                setOf(ShaderStage.Vertex, ShaderStage.Fragment),
                2,
            )))
            val before = fixture.driver.mutations.toList()
            assertFailsWith<UnsupportedOperationException> { fixture.device.createPipelineLayout(PipelineLayoutDescription(
                descriptorSets = listOf(layout),
                label = "limits",
            )) }
            assertEquals(before, fixture.driver.mutations)
        }
        DescriptorFixture().use { fixture ->
            val huge = fixture.layout(count = Int.MAX_VALUE)
            assertFailsWith<UnsupportedOperationException> { fixture.device.createPipelineLayout(PipelineLayoutDescription(
                descriptorSets = listOf(huge),
                label = "overflow",
            )) }
        }
    }
}

private class DescriptorFixture(
    objects: Boolean = true,
    legacy: Boolean = !objects,
) : AutoCloseable {
    val driver = DescriptorDriver(
        objects,
        legacy,
    )
    val device = OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler())
    private val owned = mutableListOf<AutoCloseable>()
    val targetView = image(setOf(TextureUsage.ColorAttachment, TextureUsage.Sampled))
    private val attachment = device.createAttachmentView(targetView)
    fun image(usage: Set<TextureUsage> = setOf(TextureUsage.Sampled)): GpuTextureView {
        val texture = device.createTexture(TextureDescription(
            label = "descriptor image",
            width = 64,
            height = 64,
            format = TextureFormat.Rgba8UnsignedNormalized,
            usage = usage,
        )).also { owned.add(it) }
        return device.createTextureView(texture, TextureViewDescription())
    }
    fun imported(view: GpuTextureView): GpuTextureView {
        val source = view.texture as OpenGLTexture
        val texture = device.importTexture(source.name, TextureDescription(
            label = "descriptor import",
            width = source.width,
            height = source.height,
            format = source.format,
            usage = source.usage,
        )).also { owned.add(it) }
        return device.createTextureView(texture, TextureViewDescription())
    }
    fun sampler(filter: TextureFilter = TextureFilter.Nearest, addressW: SamplerAddressMode = SamplerAddressMode.ClampToEdge): GpuSampler = device.createSampler(SamplerDescription(
        filter,
        filter,
        SamplerAddressMode.ClampToEdge,
        SamplerAddressMode.ClampToEdge,
        addressW,
    )).also { owned.add(it) }
    fun layout(binding: Int = 0, count: Int = 1): DescriptorSetLayout = DescriptorSetLayout(listOf(DescriptorBindingLayout(
        binding,
        DescriptorType.CombinedTextureSampler(),
        setOf(ShaderStage.Fragment),
        count,
    )))
    fun selection(layout: DescriptorSetLayout, images: List<Pair<GpuTextureView, GpuSampler>>): DescriptorSet = DescriptorSet(
        layout,
        listOf(DescriptorBinding(
            layout.bindings.single().binding,
            images.map { DescriptorResource.CombinedTextureSampler(
                it.first,
                it.second,
            ) },
        )),
    )
    fun binding(set: Int, binding: Int, count: Int = 1, locations: List<UniformLocation> = listOf(UniformLocation(4))): OpenGLProgramBinding = OpenGLProgramBinding(
        OpenGLShaderBinding(
            set,
            binding,
            count,
            OpenGLShaderBindingKind.CombinedTextureSampler,
            0,
            "sampler_${set}_$binding",
        ),
        setOf(ShaderStage.Fragment),
        locations = locations,
    )
    fun uniformView(offset: Long = 256, size: Long = 17): GpuBufferView {
        val buffer = device.createBuffer(BufferDescription(
            "uniform allocation",
            4096,
            setOf(heckerpowered.render.resource.buffer.BufferUsage.Uniform, heckerpowered.render.resource.buffer.BufferUsage.Index),
        )).also { owned.add(it) }
        return GpuBufferView(
            buffer,
            offset,
            size,
        )
    }
    fun uniformLayout(binding: Int = 7, minimum: Long = 0, stages: Set<ShaderStage> = setOf(ShaderStage.Fragment)): DescriptorSetLayout = DescriptorSetLayout(listOf(DescriptorBindingLayout(
        binding,
        DescriptorType.UniformBuffer(minimum),
        stages,
    )))
    fun uniformSelection(layout: DescriptorSetLayout, views: List<GpuBufferView>): DescriptorSet = DescriptorSet(
        layout,
        layout.bindings.mapIndexed { index, binding -> DescriptorBinding(
            binding.binding,
            listOf(DescriptorResource.Buffer(views[index])),
        ) },
    )
    fun uniformBinding(set: Int = 0, binding: Int = 7, block: Int = 3, required: Int = 16, stages: Set<ShaderStage> = setOf(ShaderStage.Fragment)): OpenGLProgramBinding = OpenGLProgramBinding(
        OpenGLShaderBinding(
            set,
            binding,
            1,
            OpenGLShaderBindingKind.UniformBuffer,
            4,
            "block_$block",
            "0:1:1:0:false",
        ),
        stages,
        UniformBlockIndex(block),
        requiredSizeBytes = required,
    )
    fun pipeline(layouts: List<DescriptorSetLayout>, bindings: List<OpenGLProgramBinding>): OpenGLRenderPipeline {
        val modules = ShaderStage.entries.map { stage ->
            OpenGLShaderModule(
                device,
                ShaderName(stage.ordinal + 1),
                ShaderModuleDescription(
                    stage,
                    ShaderSource(
                        ShaderLanguage.Glsl,
                        "void main() {}",
                        "descriptor fixture",
                    ),
                ),
                OpenGLShaderArtifact(
                    stage,
                    120,
                    "",
                    "",
                    emptyList(),
                    if (stage == ShaderStage.Fragment) listOf(OpenGLShaderInput(
                        0,
                        4,
                        "color",
                    )) else emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            ).also { owned.add(it) }
        }
        val shaders = OpenGLShaderStages(
            device,
            ProgramName(1),
            modules,
            "descriptor draw fixture",
            resourceInterface = OpenGLShaderInterface(
                bindings,
                emptyList(),
                emptyList(),
            ),
        ).also { owned.add(it) }
        val layout = device.createPipelineLayout(PipelineLayoutDescription(
            descriptorSets = layouts,
            label = "descriptor layout",
        )).also { owned.add(it) }
        return (device.createRenderPipeline(RenderPipelineDescription(
            label = "descriptor pipeline",
            shaders = shaders,
            layout = layout,
            colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)),
        )) as OpenGLRenderPipeline).also { owned.add(it) }
    }
    fun pass(descriptors: List<DescriptorSet>, indexBuffers: List<GpuBufferView> = emptyList(), commands: RenderPass.() -> Unit) {
        device.encode("descriptor fixture") { renderPass(RenderPassDescription(
            label = "descriptor pass",
            colorAttachments = listOf(RenderPassAttachment(attachment)),
        ), RenderPassResources(descriptors = descriptors, indexBuffers = indexBuffers), commands) }
    }
    override fun close() = terminateOnFailure {
        owned.asReversed().forEach { it.close() }
        device.close()
        driver.base.device.close()
    }
}

private class DescriptorDriver(
    private val objects: Boolean,
    private val legacy: Boolean,
) {
    val base = DrawStateDriver(
        true,
        legacyRaster = legacy,
    )
    private var active = 5
    private var nextTexture = 100
    private var nextSampler = 200
    private val textures = (0..5).associateWith { 40 + it }.toMutableMap()
    private val samplers = (0..5).associateWith { 50 + it }.toMutableMap()
    private val parameters = mutableMapOf<Int, MutableMap<Int, Int>>()
    val lodBias = (0..5).associateWith { 8f - it }.toMutableMap()
    val drawBias = mutableListOf<Map<Int, Float>>()
    val limits = mutableMapOf(0x8B4D to 8, 0x8B4C to 8, 0x8872 to 8, 0x8A2B to 8, 0x8A2D to 8, 0x8A2E to 16, 0x8A34 to 256)
    var nativeUniforms = true
    var range64 = true
    var nativeSizeLimit = Long.MAX_VALUE
    var genericUniform = 79
    val uniformPoints = (0..7).associateWith { listOf(0L, 0L, 0L) }.toMutableMap().apply {
        this[0] = listOf(70L, 0L, 0L)
        this[1] = listOf(71L, 256L, 17L)
    }
    val uniformBlocks = mutableMapOf((1 to 3) to 5, (1 to 4) to 6)
    val uniformDraws = mutableListOf<Pair<Map<Int, List<Long>>, Map<Pair<Int, Int>, Int>>>()
    val uniformCalls = mutableListOf<String>()
    var failUniformQuery: Int? = null
    var failUniformApply: Int? = null
    var failUniformRestore = false
    var observeDeletedBuffer: Int? = null
    private val nativeUniformBuffers = Proxy.newProxyInstance(OpenGLUniformBufferFunctions::class.java.classLoader, arrayOf(OpenGLUniformBufferFunctions::class.java)) { _, method, raw ->
        val args = raw ?: emptyArray()
        when (val name = method.name.substringBefore('-')) {
            "getMaximumBindings" -> 8
            "getSupports64BitRangeQueries" -> range64
            "getBoundUniformBuffer" -> {
                uniformCalls.add("queryBuffer:${args.firstOrNull()}")
                fail(name)
                if (args.isNotEmpty() && args[0] == failUniformQuery) { failUniformQuery = null; throw AssertionError("uniform query failed") }
                if (args.isEmpty()) genericUniform else uniformPoints.getValue(args[0] as Int)[0].toInt()
            }
            "getUniformBufferRange" -> {
                uniformCalls.add("queryRange:${args[0]}:${args[1]}")
                val value = uniformPoints.getValue(args[0] as Int)[if (args[1] == 0x8A29) 1 else 2]
                if (range64) value else value.coerceAtMost(Int.MAX_VALUE.toLong())
            }
            "getUniformBlockProperties" -> {
                uniformCalls.add("queryBlock:${args[0]}:${args[1]}")
                check(args[2] == 0x8A3F)
                (args[3] as IntBuffer).put(0, uniformBlocks.getValue((args[0] as Int) to (args[1] as Int)))
                null
            }
            "bindUniformBlock" -> {
                uniformBlocks[(args[0] as Int) to (args[1] as Int)] = args[2] as Int
                uniformCalls.add("block:${args[0]}:${args[1]}:${args[2]}")
                fail(name)
                null
            }
            "bindUniformBuffer" -> {
                if (args.size == 1) genericUniform = args[0] as Int else {
                    val point = args[0] as Int
                    genericUniform = args[1] as Int
                    uniformPoints[point] = listOf(genericUniform.toLong(), if (args.size == 4) args[2] as Long else 0L, if (args.size == 4) args[3] as Long else 0L)
                    if (args.size == 4 && failUniformApply == point) { failUniformApply = null; throw AssertionError("uniform apply failed") }
                    if (failUniformRestore && genericUniform == 70) throw AssertionError("uniform restore failed")
                }
                uniformCalls.add("buffer:${args.toList()}")
                fail(name)
                null
            }
            else -> error("Unexpected uniform call $name")
        }
    } as OpenGLUniformBufferFunctions
    val uniforms = mutableListOf<Pair<Int, Int>>()
    val mutations = mutableListOf<String>()
    val sampledUnits = mutableListOf<Int>()
    val drawStates = mutableListOf<Map<Int, Int>>()
    var failOnce: String? = null
    var failOnUnit: Int? = null
    var failRestore = false
    var queryError: Int? = null
    var errorOnce: String? = null
    private fun fail(name: String) {
        if (errorOnce == name) { errorOnce = null; base.nativeError = 0x0502 }
        if (failOnce == name) { failOnce = null; throw AssertionError("native $name failure") }
    }
    private fun parameters(texture: Int): MutableMap<Int, Int> = parameters.getOrPut(texture) { mutableMapOf(0x2801 to 0x2600, 0x2800 to 0x2600, 0x2802 to 0x2901, 0x2803 to 0x2901, 0x8072 to 0x2901, 0x813C to 0, 0x813D to 1000) }
    private val nativeSamplers = Proxy.newProxyInstance(OpenGLSamplerFunctions::class.java.classLoader, arrayOf(OpenGLSamplerFunctions::class.java)) { _, method, raw ->
        val args = raw ?: emptyArray()
        when (val name = method.name.substringBefore('-')) {
            "createSampler" -> nextSampler++
            "samplerParameter", "deleteSampler" -> null
            "getBoundSampler" -> samplers.getValue(active)
            "bindSampler" -> { samplers[args[0] as Int] = args[1] as Int; mutations.add(name); fail(name); null }
            else -> error(name)
        }
    } as OpenGLSamplerFunctions
    val functions = Proxy.newProxyInstance(OpenGLFunctions::class.java.classLoader, arrayOf(OpenGLFunctions::class.java)) { _, method, raw ->
        val args = raw ?: emptyArray()
        when (val name = method.name.substringBefore('-')) {
            "getUniformBuffers" -> if (nativeUniforms) nativeUniformBuffers else null
            "getMaximumBufferSizeBytes" -> nativeSizeLimit
            "deleteBuffer" -> { if (args[0] == observeDeletedBuffer) println("delete-referenced-buffer"); forward(method, args) }
            "getSamplers" -> if (objects) nativeSamplers else null
            "getSupportsLegacyTextureLodBias" -> legacy
            "getTextureUnitLodBias" -> {
                check(legacy) { "Core profile must not query legacy texture environment" }
                fail(name)
                lodBias.getValue(active)
            }
            "textureUnitLodBias" -> {
                check(legacy) { "Core profile must not mutate legacy texture environment" }
                lodBias[active] = args[0] as Float
                mutations.add(name)
                fail(name)
                null
            }
            "isTexture" -> args[0] as Int in 100 until nextTexture
            "getTextureLevelParameter" -> if (args[0] as Int > 0) 0 else forward(method, args)
            "createTexture" -> nextTexture++.also { parameters(it) }
            "getBoundTexture2D" -> textures.getValue(active)
            "bindTexture2D" -> { textures[active] = args[0] as Int; mutations.add(name); fail(name); null }
            "getActiveTextureUnit" -> active
            "activeTexture" -> {
                active = args[0] as Int
                mutations.add(name)
                if (failOnUnit == active) { failOnUnit = null; throw AssertionError("second unit selection failed") }
                fail(name)
                null
            }
            "textureParameter" -> {
                parameters(textures.getValue(active))[args[0] as Int] = args[1] as Int
                mutations.add(name)
                if (failRestore && args[0] == 0x813D && args[1] == 1000) throw AssertionError("descriptor restore failed")
                fail(name)
                null
            }
            "getTextureParameter" -> parameters(textures.getValue(active)).getValue(args[0] as Int).also { fail(name) }
            "uniformInt" -> { uniforms.add((args[0] as Int) to (args[1] as Int)); sampledUnits.add(args[1] as Int); mutations.add(name); fail(name); null }
            "getInteger" -> {
                if (queryError == args[0]) base.nativeError = 0x0502
                limits[args[0] as Int] ?: forward(method, args)
            }
            "drawElements", "drawElementsBaseVertex", "drawArraysInstanced", "drawElementsInstanced", "drawElementsInstancedBaseVertex" -> {
                uniformDraws.add(uniformPoints.toMap() to uniformBlocks.toMap())
                fail(name)
                forward(method, args)
            }
            "drawArrays" -> { uniformDraws.add(uniformPoints.toMap() to uniformBlocks.toMap()); drawStates.add(textures.toMap()); drawBias.add(lodBias.toMap()); fail(name); forward(method, args) }
            else -> forward(method, args)
        }
    } as OpenGLFunctions
    private fun forward(method: java.lang.reflect.Method, args: Array<out Any?>): Any? = try { method.invoke(base.functions, *args) } catch (failure: InvocationTargetException) { throw failure.targetException }
    fun snapshot(): List<Any> = listOf(active, textures.toMap(), samplers.toMap(), lodBias.toMap(), parameters.mapValues { it.value.toMap() }, genericUniform, uniformPoints.toMap(), uniformBlocks.toMap(), base.snapshot())
}

object OpenGLDescriptorFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { File(arguments[1]).writeText("hook ran") })
        DescriptorFixture(false).use { fixture ->
            val image = fixture.image()
            val sampler = fixture.sampler()
            val imported = if (arguments[0] == "importtexture") fixture.imported(image) else null
            val selection = fixture.selection(fixture.layout(), listOf(image to sampler))
            val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.binding(0, 0)))
            fixture.pass(listOf(selection)) {
                bindDescriptorSet(0, selection)
                bindPipeline(pipeline)
                when (arguments[0]) {
                    "texture" -> image.texture.close()
                    "importtexture" -> checkNotNull(imported).texture.close()
                    "sampler" -> sampler.close()
                    "restore" -> { fixture.driver.failRestore = true; draw(3) }
                }
            }
        }
        error("Fatal boundary returned")
    }
}

object OpenGLUniformFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { File(arguments[1]).writeText("hook ran") })
        DescriptorFixture().use { fixture ->
            val view = fixture.uniformView()
            val selection = fixture.uniformSelection(fixture.uniformLayout(), listOf(view))
            val pipeline = fixture.pipeline(listOf(selection.layout), listOf(fixture.uniformBinding()))
            fixture.driver.observeDeletedBuffer = (view.buffer as OpenGLBuffer).name.value
            fixture.pass(listOf(selection)) {
                if (arguments[0] != "declared") bindDescriptorSet(0, selection)
                if (arguments[0] == "restore") {
                    bindPipeline(pipeline)
                    fixture.driver.failUniformRestore = true
                    draw(3)
                } else view.buffer.close()
            }
        }
        error("Fatal boundary returned")
    }
}
