/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.pass

import heckerpowered.math.AffineTransforms
import heckerpowered.math.Matrices
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.pass.*
import heckerpowered.render.color.Color
import heckerpowered.render.engine.geometry.*
import heckerpowered.render.engine.image.*
import heckerpowered.render.engine.material.*
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.scene.*
import heckerpowered.render.engine.scene.drawing.*
import heckerpowered.render.engine.shader.binding.VertexInputMapping
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.shader.program.*
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.pipeline.color.*
import heckerpowered.render.pipeline.depthstencil.*
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.pipeline.primitive.*
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import kotlin.test.*

class CompositingPreparationTest {
    @Test
    fun builtinAndExternalCoverageOperationsUseTheSamePreparedPath() = withFixture { fixture ->
        val target = fixture.image("coverage")
        val external = CompositingMode { format, source, destination ->
            require(source?.alphaQuantity == AlphaQuantity.Coverage && destination == AlphaQuantity.Coverage)
            ColorTargetState(format, BlendState.PremultipliedAlpha)
        }
        val bound = fixture.element()
        val draws = fixture.prepare(
            replacementPass("same path", target),
            listOf(
                bound.copy(composition = CompositingMode.SourceOver, depthWrite = false),
                bound.copy(composition = external, depthWrite = false),
            ),
        )
        assertEquals(draws[0].pipeline.colorTargets, draws[1].pipeline.colorTargets)
        assertEquals(draws[0].pipeline.depthStencil, draws[1].pipeline.depthStencil)
    }

    @Test
    fun unknownRgbaDestinationAndFalseCoverageClearAreRejected() = withFixture { fixture ->
        val target = attachment(TextureFormat.Rgba16Float)
        val element = fixture.element(CompositingMode.SourceOver)
        for (alpha in listOf(0f, 2f)) {
            val description = cleared(target, Color(0f, 0f, 0f, alpha))
            assertFailsWith<IllegalArgumentException> { fixture.prepare(description, listOf(element)) }
        }
        assertFailsWith<IllegalArgumentException> {
            fixture.prepare(cleared(target, Color(0f, 0f, 0f, 2f)), listOf(element), mapOf(0 to AlphaQuantity.Coverage))
        }
        fixture.prepare(cleared(target, Color.TransparentBlack), listOf(element), mapOf(0 to AlphaQuantity.Coverage))
    }

    @Test
    fun borrowedCoverageLoadRequiresAnInitialContentContract() = withFixture { fixture ->
        val description = loaded(attachment(TextureFormat.Rgba16Float))
        val element = fixture.element(CompositingMode.SourceOver)
        assertFailsWith<IllegalArgumentException> { fixture.prepare(description, listOf(element), mapOf(0 to AlphaQuantity.Coverage)) }
        fixture.prepare(description, listOf(element), mapOf(0 to AlphaQuantity.Coverage), mapOf(0 to ColorContent(AlphaQuantity.Coverage, AlphaRepresentation.Premultiplied)))
    }

    @Test
    fun discardCannotEstablishCoverageEvenForPartialReplace() = withFixture { fixture ->
        val description = discarded(attachment(TextureFormat.Rgba16Float))
        for (mode in listOf(CompositingMode.SourceOver, CompositingMode.Replace)) {
            assertFailsWith<IllegalArgumentException> { fixture.prepare(description, listOf(fixture.element(mode)), mapOf(0 to AlphaQuantity.Coverage)) }
        }
        val collection = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {
            clip(ScissorRectangle(0, 0, 1, 1)) { geometry(fixture.element()) }
        }
        assertFailsWith<IllegalArgumentException> {
            fixture.processor.prepare(RasterPass(description, collection, fixture.view, alphaQuantities = mapOf(0 to AlphaQuantity.Coverage)), fixture.root)
        }
    }

    @Test
    fun previousReplaceDoesNotProveDiscardedContentsForLaterBlend() = withFixture { fixture ->
        assertFailsWith<IllegalArgumentException> {
            fixture.prepare(discarded(attachment(TextureFormat.Rgba16Float)), listOf(fixture.element(), fixture.element(CompositingMode.Add)))
        }
    }

    @Test
    fun customSourceFactorCanReadDestinationWithAZeroDestinationTerm() = withFixture { fixture ->
        val operation = CompositingMode { format, _, _ -> ColorTargetState(format,
            BlendState(BlendComponent(BlendFactor.DestinationColor, BlendFactor.Zero), BlendComponent(BlendFactor.One, BlendFactor.Zero))) }
        assertFailsWith<IllegalArgumentException> { fixture.prepare(discarded(attachment(TextureFormat.Rgba16Float)), listOf(fixture.element(operation))) }
    }

    @Test
    fun sourceOnlyCustomEquationNeedsNoDiscardedDestination() = withFixture { fixture ->
        val operation = CompositingMode { format, _, _ -> ColorTargetState(format,
            BlendState(BlendComponent(BlendFactor.One, BlendFactor.Zero), BlendComponent(BlendFactor.One, BlendFactor.Zero))) }
        fixture.prepare(discarded(attachment(TextureFormat.Rgba16Float)), listOf(fixture.element(operation)))
    }

    @Test
    fun missingAlphaDoesNotInitializeStoredRgbForBlending() = withFixture { fixture ->
        for (format in listOf(TextureFormat.R8UnsignedNormalized, TextureFormat.Rg16Float, TextureFormat.R32Float)) {
            val target = attachment(format)
            fixture.prepare(loaded(target), listOf(fixture.element(CompositingMode.SourceOver)))
            for (mode in listOf(CompositingMode.SourceOver, CompositingMode.Add)) {
                assertFailsWith<IllegalArgumentException> { fixture.prepare(discarded(target), listOf(fixture.element(mode))) }
            }
        }
    }

    @Test
    fun missingAlphaFactorAndAbsentWriteMaskUseFormatFacts() = withFixture { fixture ->
        val target = attachment(TextureFormat.R32Float)
        val sourceOnly = CompositingMode { format, _, _ -> ColorTargetState(format,
            BlendState(BlendComponent(BlendFactor.DestinationAlpha, BlendFactor.Zero), BlendComponent(BlendFactor.One, BlendFactor.Zero))) }
        fixture.prepare(discarded(target), listOf(fixture.element(sourceOnly)))
        val absentAlpha = ColorWriteMask(false, false, false, true)
        val operation = CompositingMode { format, _, _ -> ColorTargetState(format, BlendState.Additive, absentAlpha) }
        val draw = fixture.prepare(discarded(target), listOf(fixture.element(operation))).single()
        assertSame(absentAlpha, draw.pipeline.colorTargets.single().writeMask)
    }

    @Test
    fun coverageCannotBeWeakenedAndSignalAdditionRetainsItsMeaning() = withFixture { fixture ->
        val coverage = fixture.image("coverage")
        assertFailsWith<IllegalArgumentException> { fixture.prepare(replacementPass("coverage", coverage), listOf(fixture.element(CompositingMode.Add))) }
        assertFailsWith<IllegalArgumentException> { fixture.prepare(replacementPass("coverage", coverage), listOf(fixture.element()), mapOf(0 to AlphaQuantity.Signal)) }
        val signal = fixture.image("signal", AlphaQuantity.Signal)
        val draw = fixture.prepare(replacementPass("signal", signal), listOf(fixture.element(CompositingMode.Add, AlphaQuantity.Signal))).single()
        assertEquals(BlendState.Additive, draw.pipeline.colorTargets.single().blend)
        assertFailsWith<IllegalArgumentException> { fixture.prepare(replacementPass("signal", signal), listOf(fixture.element(CompositingMode.SourceOver, AlphaQuantity.Signal))) }
    }

    @Test
    fun resolveCoverageRequiresTheSourcePassToPreserveCoverage() = withFixture { fixture ->
        val destination = fixture.image("resolve")
        val source = attachment(TextureFormat.Rgba16Float, SampleCount.Four)
        val description = RenderPassDescription(
            "resolve",
            colorAttachments = listOf(RenderPassAttachment(source, AttachmentOperations(AttachmentLoadOperation.Clear(Color.TransparentBlack), AttachmentStoreOperation.Discard))),
            colorResolves = listOf(ColorAttachmentResolve(0, ImageRegion.Attachment(destination.attachment))),
        )
        fixture.prepare(description, listOf(fixture.element(CompositingMode.SourceOver)))
        assertFailsWith<IllegalArgumentException> { fixture.prepare(description, listOf(fixture.element(CompositingMode.Add, AlphaQuantity.Signal))) }
    }

    @Test
    fun mrtRetainsSlotGapsStateMasksAndImmutableMembership() = withFixture { fixture ->
        val coverage = fixture.image("coverage")
        val signal = fixture.image("signal", AlphaQuantity.Signal)
        val modes = linkedMapOf<Int, CompositingMode>(0 to CompositingMode.SourceOver, 2 to CompositingMode.Add)
        val element = GeometryElement(fixture.shader(mapOf(0 to FragmentOutput(AlphaRepresentation.Premultiplied), 2 to FragmentOutput(alphaQuantity = AlphaQuantity.Signal))).bind(Unit).shading, modes)
        modes.clear()
        assertEquals(setOf(0, 2), element.compositions.keys)
        val description = RenderPassDescription("MRT", colorAttachments = listOf(replacementPass("c", coverage).colorAttachments.single(), null, replacementPass("s", signal).colorAttachments.single()))
        val targets = fixture.prepare(description, listOf(element)).single().pipeline.colorTargets
        assertEquals(ColorWriteMask.None, targets[1].writeMask)
        assertEquals(BlendState.Additive, targets[2].blend)
        assertFailsWith<UnsupportedOperationException> { (targets as MutableList).clear() }
    }

    @Test
    fun depthWritesAreExplicitAndIndependentOfComposition() = withFixture { fixture ->
        val color = fixture.image("coverage")
        val description = RenderPassDescription(
            "depth",
            colorAttachments = replacementPass("color", color).colorAttachments,
            depthAttachment = RenderPassAttachment(depthAttachment(), AttachmentOperations(AttachmentLoadOperation.Clear(1f), AttachmentStoreOperation.Store)),
        )
        val elements = listOf(true, false).map { fixture.element(CompositingMode.SourceOver).copy(depthWrite = it) }
        assertEquals(listOf(true, false), fixture.prepare(description, elements).map { it.pipeline.depthStencil!!.depth!!.writeEnabled })
        val exact = DepthStencilState(TextureFormat.Depth32Float, DepthState(CompareFunction.GreaterOrEqual, false))
        assertEquals(exact, fixture.prepare(description, listOf(fixture.element().copy(depthStencil = exact))).single().pipeline.depthStencil)
    }

    @Test
    fun externalDestinationOverDeclaresCoverageOnTheExactReturnedState() = withFixture { fixture ->
        val target = fixture.image("destination over")
        var targetCalls = 0
        var contractCalls = 0
        val state = ColorTargetState(TextureFormat.Rgba16Float, blendState {
            color(BlendFactor.OneMinusDestinationAlpha, BlendFactor.One)
            alpha(BlendFactor.OneMinusDestinationAlpha, BlendFactor.One)
        })
        val external = object : CompositingMode {
            override fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaQuantity?): ColorTargetState {
                targetCalls++
                require(format == state.format && source?.alphaQuantity == AlphaQuantity.Coverage && destination == AlphaQuantity.Coverage)
                return state
            }
            override fun preservesCoverage(source: FragmentOutput, target: ColorTargetState): Boolean {
                contractCalls++
                assertSame(state, target)
                return source.representation == AlphaRepresentation.Premultiplied && source.alphaQuantity == AlphaQuantity.Coverage
            }
        }
        val draw = fixture.prepare(replacementPass("destination over", target), listOf(fixture.element(external))).single()
        assertSame(state, draw.pipeline.colorTargets.single())
        assertEquals(1, targetCalls)
        assertEquals(1, contractCalls)
        assertFailsWith<IllegalArgumentException> {
            fixture.prepare(replacementPass("no declaration", target), listOf(fixture.element(CompositingMode { _, _, _ -> state })))
        }
    }

    @Test
    fun externalCoverageContractCannotBypassOutputOrDestinationInitialization() = withFixture { fixture ->
        val owned = fixture.image("coverage")
        var contractCalls = 0
        val external = object : CompositingMode {
            override fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaQuantity?) = ColorTargetState(format, blendState {
                color(BlendFactor.OneMinusDestinationAlpha, BlendFactor.One)
                alpha(BlendFactor.OneMinusDestinationAlpha, BlendFactor.One)
            })
            override fun preservesCoverage(source: FragmentOutput, target: ColorTargetState): Boolean {
                contractCalls++
                return true
            }
        }
        val element = fixture.element(external)
        assertFailsWith<IllegalArgumentException> {
            fixture.prepare(discarded(owned.attachment), listOf(element))
        }
        assertFailsWith<IllegalArgumentException> {
            fixture.prepare(loaded(attachment(TextureFormat.Rgba16Float)), listOf(element), mapOf(0 to AlphaQuantity.Coverage))
        }
        assertFailsWith<IllegalArgumentException> {
            fixture.prepare(replacementPass("missing output", owned), listOf(fixture.shader(emptyMap()).bind(Unit).copy(composition = external)))
        }
        assertEquals(0, contractCalls)
        assertFailsWith<IllegalArgumentException> {
            fixture.prepare(discarded(attachment(TextureFormat.Rgba16Float)), listOf(element))
        }
        assertEquals(0, contractCalls)
    }

    @Test
    fun explicitViewDepthComparisonPreservesCustomStencilAndWriteSettings() = withFixture { fixture ->
        val color = fixture.image("depth view")
        val attachment = attachment(TextureFormat.Depth24UnsignedNormalizedStencil8)
        val description = RenderPassDescription(
            "depth view",
            colorAttachments = replacementPass("color", color).colorAttachments,
            depthAttachment = RenderPassAttachment(attachment, AttachmentOperations(AttachmentLoadOperation.Clear(1f), AttachmentStoreOperation.Store)),
            stencilAttachment = RenderPassAttachment(attachment, AttachmentOperations(AttachmentLoadOperation.Clear(0u.toUByte()), AttachmentStoreOperation.Store)),
        )
        val stencil = StencilState(StencilFaceState(CompareFunction.Equal), readMask = 7u.toUByte(), writeMask = 3u.toUByte())
        val state = DepthStencilState(attachment.format, DepthState(CompareFunction.Equal, false), stencil)
        val fixed = fixture.element().copy(depthStencil = state)
        val following = fixed.copy(usesViewDepthCompare = true)
        for (compare in listOf(CompareFunction.Less, CompareFunction.Greater)) {
            val draws = fixture.prepare(description, listOf(fixed, following), depthCompare = compare)
            assertEquals(state, draws[0].pipeline.depthStencil)
            assertEquals(state.copy(depth = state.depth!!.copy(compareFunction = compare)), draws[1].pipeline.depthStencil)
        }
        val writes = fixture.prepare(description, listOf(following.copy(depthWrite = true)), depthCompare = CompareFunction.Greater).single().pipeline.depthStencil!!
        assertEquals(stencil, writes.stencil)
        assertEquals(DepthState(CompareFunction.Greater, true), writes.depth)
        assertFailsWith<IllegalArgumentException> {
            fixture.prepare(description, listOf(following), visibility = DepthMode.SeeThrough)
        }
        val noDepth = following.copy(depthStencil = state.copy(depth = null))
        assertEquals(stencil, fixture.prepare(description, listOf(noDepth), visibility = DepthMode.SeeThrough).single().pipeline.depthStencil!!.stencil)
    }

    @Test
    fun externalMaskedReplacementCannotBreakPremultipliedCoverage() = withFixture { fixture ->
        val target = fixture.image("masked coverage")
        val masks = listOf(
            ColorWriteMask(false, false, false, true),
            ColorWriteMask(true, false, false, true),
            ColorWriteMask(true, true, true, false),
        )
        for (mask in masks) {
            val external = CompositingMode { format, _, _ -> ColorTargetState(format, writeMask = mask) }
            val clear = if (mask.alpha) Color(1f, 1f, 1f, 1f) else Color.TransparentBlack
            assertFailsWith<IllegalArgumentException> {
                fixture.prepare(cleared(target.attachment, clear), listOf(fixture.element(external)))
            }
        }
    }

    @Test
    fun completeReplacementSourceOverAndUnchangedWritesRetainCoverage() = withFixture { fixture ->
        val target = fixture.image("complete coverage")
        val complete = listOf<BlendState?>(null, BlendState.PremultipliedAlpha)
        for (blend in complete) {
            val external = CompositingMode { format, _, _ -> ColorTargetState(format, blend) }
            fixture.prepare(replacementPass("complete write", target), listOf(fixture.element(external)))
        }
        val straight = fixture.shader(mapOf(0 to FragmentOutput(AlphaRepresentation.Straight))).bind(Unit)
        val straightOver = CompositingMode { format, _, _ -> ColorTargetState(format, BlendState.StraightAlpha) }
        fixture.prepare(replacementPass("straight source over", target), listOf(straight.copy(composition = straightOver)))
        val unchanged = blendState {
            color(BlendFactor.Zero, BlendFactor.One)
            alpha(BlendFactor.Zero, BlendFactor.One)
        }
        for (mask in listOf(ColorWriteMask(false, false, false, true), ColorWriteMask(true, false, false, false), ColorWriteMask.All)) {
            val external = CompositingMode { format, _, _ -> ColorTargetState(format, unchanged, mask) }
            fixture.prepare(replacementPass("unchanged", target), listOf(fixture.element(external, AlphaQuantity.Signal)))
        }
    }

    @Test
    fun validExternalAlphaOnlySourceOverUsesItsExplicitPreservationContract() = withFixture { fixture ->
        val target = fixture.image("declared partial coverage")
        val mask = ColorWriteMask(false, false, false, true)
        val state = ColorTargetState(target.attachment.format, BlendState.PremultipliedAlpha, mask)
        var calls = 0
        val external = object : CompositingMode {
            override fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaQuantity?): ColorTargetState {
                require(format == state.format && destination == AlphaQuantity.Coverage)
                return state
            }
            override fun preservesCoverage(source: FragmentOutput, target: ColorTargetState): Boolean {
                calls++
                assertSame(state, target)
                // Coverage source-over alpha cannot decrease destination alpha while RGB is retained.
                return source.alphaQuantity == AlphaQuantity.Coverage && target.blend?.alpha == BlendState.PremultipliedAlpha.alpha && target.writeMask == mask
            }
        }
        val element = fixture.element(external)
        fixture.prepare(cleared(target.attachment, Color(1f, 1f, 1f, 1f)), listOf(element))
        assertEquals(1, calls)
        val undeclared = CompositingMode { _, _, _ -> state }
        assertFailsWith<IllegalArgumentException> {
            fixture.prepare(replacementPass("undeclared partial", target), listOf(fixture.element(undeclared)))
        }
        assertFailsWith<IllegalArgumentException> { fixture.prepare(discarded(target.attachment), listOf(element)) }
        assertEquals(1, calls)
    }

    @Test
    fun independentRgbAdditionRequiresSignalOrUnknownHostInsteadOfPureCoverage() = withFixture { fixture ->
        val coverage = fixture.image("pure coverage")
        for (source in listOf(AlphaQuantity.Coverage, AlphaQuantity.Signal)) {
            val addition = fixture.element(CompositingMode.AddColorPreserveAlpha, source)
            assertFailsWith<IllegalArgumentException> {
                fixture.prepare(replacementPass("pure coverage", coverage), listOf(addition))
            }
        }
        val emission = fixture.element(CompositingMode.AddColorPreserveAlpha, AlphaQuantity.Signal)
        val signal = fixture.image("signal", AlphaQuantity.Signal)
        fixture.prepare(replacementPass("signal addition", signal), listOf(emission))
        fixture.prepare(loaded(attachment(TextureFormat.Rgba16Float)), listOf(emission))
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val fixture = Fixture()
        try { block(fixture) } finally { fixture.root.close() }
        assertEquals(fixture.created, fixture.closed)
    }

    private class Fixture {
        val root = ResourceLifetime.build { this }
        var created = 0
        var closed = 0
        private fun <T> owned(type: Class<T>): T {
            created++
            return proxy(type) { operation, _ -> check(operation == "close"); terminateOnFailure { closed++; Unit } }
        }
        private val sampler = proxy(GpuSampler::class.java) { _, _ -> error("No sampler access") }
        val device = proxy(GraphicsDevice::class.java) { operation, arguments -> when (operation) {
            "createShaderModule" -> owned(ShaderModule::class.java)
            "createShaderStages" -> owned(ShaderStages::class.java)
            "createPipelineLayout" -> owned(PipelineLayout::class.java)
            "createTexture" -> {
                val description = arguments[0] as TextureDescription
                created++
                proxy(GpuTexture::class.java) { property, _ -> when (property) {
                    "getFormat" -> description.format; "getWidth" -> description.width; "getHeight" -> description.height
                    "getUsage" -> description.usage; "getDimension" -> description.dimension; "getSampleCount" -> description.sampleCount
                    "getDepth", "getMipLevelCount", "getArrayLayerCount" -> 1
                    "close" -> terminateOnFailure { closed++; Unit }; else -> error(property)
                } }
            }
            "createTextureView" -> {
                val texture = arguments[0] as GpuTexture
                proxy(GpuTextureView::class.java) { property, _ -> when (property) {
                    "getTexture" -> texture; "getFormat" -> texture.format; "getWidth" -> texture.width; "getHeight" -> texture.height
                    "getDimension" -> TextureViewDimension.TwoDimensional; "getBaseMipLevel", "getBaseArrayLayer" -> 0
                    "getDepth", "getMipLevelCount", "getArrayLayerCount" -> 1; "getAspects" -> setOf(TextureAspect.Color); else -> error(property)
                } }
            }
            "createAttachmentView" -> { val view = arguments[0] as GpuTextureView; attachment(view.format) }
            else -> error(operation)
        } }
        val images = RenderImageStore(device, root, sampler)
        val processor = RasterPassProcessor(
            device,
            RenderElementPassProcessors().apply {
                install(GeometryElement::class.java, GeometryElementPassProcessor(ShaderRealizations(device, root)))
            },
            images,
        )
        val view = ViewParameters(Matrices.Identity, 4, 4, CompareFunction.Less)
        fun image(name: String, quantity: AlphaQuantity = AlphaQuantity.Coverage) = images.image(name, ImageSize(4, 4), TextureFormat.Rgba16Float, quantity)
        fun shader(outputs: Map<Int, FragmentOutput>): MeshShader<Unit> = MeshShader(shaderDefinition("composition") {
            native(
                listOf(ShaderModuleDescription(ShaderStage.Vertex, ShaderSource(ShaderLanguage.Glsl, "void main(){}", label = "composition vertex")), ShaderModuleDescription(ShaderStage.Fragment, ShaderSource(ShaderLanguage.Glsl, "void main(){}", label = "composition fragment"))),
                ShaderInputLayout(
                    VertexInputMapping(emptyList()),
                    emptyList(),
                    emptyList(),
                ),
            )
            replaySafe()
            noColorOutputs()
            outputs.forEach { [location, meaning] -> output(location, meaning) }
        }) { MeshShaderInput(ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState(PrimitiveTopology.TriangleList))) }
        fun element(mode: CompositingMode = CompositingMode.Replace, quantity: AlphaQuantity = AlphaQuantity.Coverage): GeometryElement =
            shader(mapOf(0 to FragmentOutput(AlphaRepresentation.Premultiplied, quantity))).bind(Unit).copy(composition = mode)
        fun prepare(description: RenderPassDescription, elements: List<GeometryElement>, alphaQuantities: Map<Int, AlphaQuantity> = emptyMap(), initial: Map<Int, ColorContent> = emptyMap(), depthCompare: CompareFunction = view.depthCompare, visibility: DepthMode = DepthMode.Scene) =
            processor.prepare(RasterPass(description, RenderSubmissionList(elements.map { RenderSubmission(it, ObjectSubmitContext(AffineTransforms.Identity), visibility = visibility) }), ViewParameters(Matrices.Identity, 4, 4, depthCompare), alphaQuantities = alphaQuantities, initialColors = initial), root).draws
    }

    companion object {
        private fun attachment(format: TextureFormat, samples: SampleCount = SampleCount.One): RenderAttachment = proxy(RenderAttachment::class.java) { property, _ -> when (property) {
            "getWidth", "getHeight" -> 4; "getArrayLayerCount" -> 1; "getFormat" -> format; "getSampleCount" -> samples
            "getAspects" -> buildSet {
                if (format.isColor) add(TextureAspect.Color)
                if (format.hasDepth) add(TextureAspect.Depth)
                if (format.hasStencil) add(TextureAspect.Stencil)
            }
            else -> error(property)
        } }
        private fun depthAttachment(): RenderAttachment = proxy(RenderAttachment::class.java) { property, _ -> when (property) {
            "getWidth", "getHeight" -> 4; "getArrayLayerCount" -> 1; "getFormat" -> TextureFormat.Depth32Float; "getSampleCount" -> SampleCount.One
            "getAspects" -> setOf(TextureAspect.Depth); else -> error(property)
        } }
        private fun cleared(target: RenderAttachment, color: Color) = RenderPassDescription("clear", colorAttachments = listOf(RenderPassAttachment(target, AttachmentOperations(AttachmentLoadOperation.Clear(color), AttachmentStoreOperation.Store))))
        private fun loaded(target: RenderAttachment) = RenderPassDescription("load", colorAttachments = listOf(RenderPassAttachment<Color>(target)))
        private fun discarded(target: RenderAttachment) = RenderPassDescription("discard", colorAttachments = listOf(RenderPassAttachment(target, AttachmentOperations<Color>(AttachmentLoadOperation.Discard, AttachmentStoreOperation.Store))))
        @Suppress("UNCHECKED_CAST")
        private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, arguments -> invoke(method.name.substringBefore('-'), arguments ?: emptyArray()) } as T
    }
}
