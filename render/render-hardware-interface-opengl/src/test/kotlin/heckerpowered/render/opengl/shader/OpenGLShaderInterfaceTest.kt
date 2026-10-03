/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.shader

import heckerpowered.render.opengl.RecordingCompiler

import heckerpowered.render.opengl.*
import heckerpowered.render.opengl.function.*
import heckerpowered.render.pipeline.*
import heckerpowered.render.pipeline.vertex.*
import heckerpowered.render.shader.*
import heckerpowered.render.shader.binding.*
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.test.*

class OpenGLShaderInterfaceTest {
    @Test
    fun ordinaryUniformVariantIsExplicitAndPreservesRawMatrixMetadata() {
        for (legacyPixel in listOf(true, false)) InterfaceDriver(false, legacyPixel).use { driver ->
            val binding = blockBinding().copy(name = "rhi_block_0_9", blockSignature = "0:4:1:0:false;16:4:4:16:true")
            val prefix = "rhi_uniform_0_9.${binding.name}_member_"
            driver.uniforms += Variable(prefix + 0, 0x8B52)
            driver.uniforms += Variable(prefix + 1, 0x8B5C)
            driver.locations[prefix + 0] = 3
            driver.locations[prefix + 1] = 4
            val input = artifact(bindings = listOf(binding)).copy(glslVersion = 140, plainUniformSource = "#version 120\nvoid main() {}")
            val result = driver.reflect(input)
            assertEquals(120, (driver.sources.single().substringAfter("#version ").substringBefore('\n')).toInt())
            val selected = result.bindings.single()
            assertEquals(UniformBlockIndex.Missing, selected.block)
            assertTrue(selected.ordinaryMembers[1].member.rowMajor)
            val scratch = selected.ordinaryMembers[1].matrix
            assertNotNull(scratch)
            assertSame(scratch, selected.ordinaryMembers[1].matrix)
            assertEquals(80, selected.requiredSizeBytes)
        }
        InterfaceDriver(false).use { driver ->
            assertFailsWith<UnsupportedOperationException> { driver.link(artifact(bindings = listOf(blockBinding())).copy(glslVersion = 140)) }
            assertTrue(driver.sources.isEmpty())
        }
    }

    @Test
    fun ordinaryLayoutChecksInactiveMetadataAndPreservesReorderedNonOverlappingMembers() {
        for (signature in listOf("0:1:1:0:false;0:1:1:0:false", "0:1:1:4:false", "0:1:1:0:true", "80:1:1:0:false")) {
            InterfaceDriver(false).use { driver ->
                val binding = blockBinding().copy(blockSignature = signature)
                assertFailsWith<IllegalArgumentException> { driver.link(artifact(bindings = listOf(binding)).copy(glslVersion = 140, plainUniformSource = "#version 120\nvoid main() {}")) }
            }
        }
        InterfaceDriver(false).use { driver ->
            val binding = blockBinding().copy(sizeBytes = 20, blockSignature = "16:1:1:0:false;0:1:1:0:false")
            val result = driver.reflect(artifact(bindings = listOf(binding)).copy(glslVersion = 140, plainUniformSource = "#version 120\nvoid main() {}"))
            assertTrue(result.bindings.isEmpty())
        }
    }

    @Test
    fun ordinaryMembersShareCapacityWithPushAndRejectWrongNativeTypes() {
        for (wrongType in listOf(false, true)) InterfaceDriver(false).use { driver ->
            val binding = blockBinding().copy(name = "rhi_block_0_9", sizeBytes = 4, blockSignature = "0:1:1:0:false")
            val name = "rhi_uniform_0_9.${binding.name}_member_0"
            driver.uniforms += Variable(name, if (wrongType) 0x1404 else 0x1406)
            driver.locations[name] = 3
            val push = OpenGLPushConstantMember(0, 4, 1, 0, false, "rhi_push_vertex.member_0")
            driver.uniforms += Variable(push.name, 0x8B52)
            driver.locations[push.name] = 7
            driver.components = 4
            assertFailsWith<IllegalArgumentException> { driver.link(artifact(bindings = listOf(binding), pushes = listOf(push)).copy(glslVersion = 140, plainUniformSource = "#version 120\nvoid main() {}")) }
        }
    }

    @Test
    fun generatedVertexAndInstanceIdsDoNotBecomeUserVertexInputs() {
        InterfaceDriver(false).use { driver ->
            driver.attributes.addAll(listOf(
                Variable(
                    "gl_VertexID",
                    0x1404,
                ),
                Variable(
                    "gl_InstanceID",
                    0x1404,
                ),
            ))
            val stages = driver.link(artifact())
            val reflected = context(driver.device) { stages.validateInterface(null, VertexState.Empty) }
            assertTrue(reflected.inputs.isEmpty())
            assertTrue(driver.linkQueries.none { it.startsWith("attributeLocation:gl_") })
        }
        InterfaceDriver(false).use { driver ->
            val input = OpenGLShaderInput(
                7,
                2,
                "coordinate",
            )
            driver.attributes.addAll(listOf(
                Variable(
                    "gl_VertexID",
                    0x1404,
                ),
                Variable(
                    input.name,
                    0x8B50,
                ),
            ))
            driver.attributeLocations[input.name] = 7
            val reflected = driver.reflect(artifact(inputs = listOf(input)))
            assertEquals(listOf(input), reflected.inputs)
        }
    }

    @Test
    fun generatedIdsDoNotRelaxUserAttributeTypeCountOrLocationValidation() {
        for (mismatch in 0..2) {
            InterfaceDriver(false).use { driver ->
                driver.attributes.add(Variable(
                    "gl_VertexID",
                    0x1404,
                ))
                driver.attributes.add(Variable(
                    "coordinate",
                    if (mismatch == 0) 0x1404 else 0x8B50,
                    if (mismatch == 1) 2 else 1,
                ))
                driver.attributeLocations["coordinate"] = if (mismatch == 2) 6 else 7
                assertFailsWith<IllegalArgumentException> {
                    driver.link(artifact(inputs = listOf(OpenGLShaderInput(
                        7,
                        2,
                        "coordinate",
                    ))))
                }
                assertEquals(listOf(1), driver.deletedPrograms)
            }
        }
    }

    @Test
    fun conventionalBuiltinsAndUndeclaredOrdinaryInputsStillReject() {
        for (name in listOf("gl_Vertex", "gl_Color", "undeclared", "user_gl_VertexID")) {
            InterfaceDriver(false).use { driver ->
                driver.attributes.add(Variable(
                    name,
                    0x8B52,
                ))
                assertFailsWith<IllegalArgumentException> { driver.link(artifact()) }
                assertEquals(listOf(1), driver.deletedPrograms)
            }
        }
    }

    @Test
    fun artifactWithNoActiveResourcesNeedsNoLayoutOrUboQueries() {
        InterfaceDriver(false).use { driver ->
            val stages = driver.link(artifact())
            driver.queries.clear()
            val reflected = context(driver.device) { stages.validateInterface(null, VertexState.Empty) }
            assertTrue(reflected.requiredSets.isEmpty())
            assertTrue(driver.queries.isEmpty())
            assertEquals(listOf("program:35718", "program:35721"), driver.linkQueries)
        }
    }

    @Test
    fun ordinaryPushUsesGlsl120AndCountsMembersWithoutPaddingOrMatrixStride() {
        InterfaceDriver(false).use { driver ->
            val member = OpenGLPushConstantMember(1024, 4, 4, 32, true, "rhi_push_vertex.member_0")
            driver.uniforms.add(Variable(member.name, 0x8B5C))
            driver.locations[member.name] = 13
            driver.components = 16
            val stages = driver.link(artifact(pushes = listOf(member)))
            assertTrue(driver.sources.single().startsWith("#version 120"))
            val description = PipelineLayoutDescription(pushConstants = PushConstantLayout(listOf(PushConstantRange(setOf(ShaderStage.Vertex), 1024, 112))), label = "ordinary push")
            val layout = driver.device.createPipelineLayout(description) as OpenGLPipelineLayout
            try {
                val reflected = context(driver.device) { stages.validateInterface(layout, VertexState.Empty) }
                assertEquals(1136, layout.pushConstantSizeBytes)
                assertEquals(13, reflected.pushConstants.single().location.value)
                assertEquals(member, reflected.pushConstants.single().member)
                assertTrue(driver.queries.none { it.startsWith("block:") || it.startsWith("properties:") })
                assertEquals(listOf("program:35718", "program:35719", "uniform:0", "location:${member.name}", "integer:35658", "program:35721"), driver.linkQueries)
                assertFailsWith<IllegalArgumentException> {
                    reflected.validate(PipelineLayoutDescription(pushConstants = PushConstantLayout(listOf(PushConstantRange(setOf(ShaderStage.Vertex), 1024, 64))), label = "short"), VertexState.Empty)
                }
                assertFailsWith<IllegalArgumentException> { reflected.validate(null, VertexState.Empty) }
            } finally {
                layout.close()
            }
        }
    }

    @Test
    fun actualOrdinaryPushComponentCapacityIsCheckedAtLink() {
        InterfaceDriver(false).use { driver ->
            val member = OpenGLPushConstantMember(0, 4, 4, 16, false, "push.matrix")
            driver.uniforms.add(Variable(member.name, 0x8B5C))
            driver.locations[member.name] = 2
            driver.components = 15
            assertFailsWith<IllegalArgumentException> { driver.link(artifact(pushes = listOf(member))) }
            assertEquals(listOf(1), driver.deletedPrograms)
        }
    }

    @Test
    fun sparseSamplerAddressAndOptimizedArrayTailRetainExplicitElementLocations() {
        InterfaceDriver(false).use { driver ->
            val binding = OpenGLShaderBinding(2, 9, 4, OpenGLShaderBindingKind.CombinedTextureSampler, 0, "materialImages")
            driver.uniforms.add(Variable("materialImages[0]", 0x8B5E, 2))
            driver.locations.putAll(mapOf("materialImages[0]" to 4, "materialImages[1]" to 17))
            val reflected = driver.reflect(artifact(ShaderStage.Fragment, bindings = listOf(binding)))
            val description = PipelineLayoutDescription(listOf(DescriptorSetLayout.Empty, DescriptorSetLayout.Empty, DescriptorSetLayout(listOf(DescriptorBindingLayout(9, DescriptorType.CombinedTextureSampler(), setOf(ShaderStage.Fragment), 4)))), label = "samplers")
            reflected.validate(description, VertexState.Empty)
            assertEquals(setOf(2), reflected.requiredSets)
            assertEquals(listOf(4, 17), reflected.bindings.single().locations.map { it.value })
            assertEquals(binding, reflected.bindings.single().declaration)
            val wrong = PipelineLayoutDescription(listOf(DescriptorSetLayout.Empty, DescriptorSetLayout.Empty, DescriptorSetLayout(listOf(DescriptorBindingLayout(9, DescriptorType.CombinedTextureSampler(), setOf(ShaderStage.Vertex), 4)))), label = "visibility")
            assertFailsWith<IllegalArgumentException> { reflected.validate(wrong, VertexState.Empty) }
        }
    }

    @Test
    fun optimizedOutResourcesAndPushesDoNotRequireBindingsOrValues() {
        InterfaceDriver(false).use { driver ->
            val binding = OpenGLShaderBinding(3, 5, 1, OpenGLShaderBindingKind.CombinedTextureSampler, 0, "unused")
            val member = OpenGLPushConstantMember(0, 1, 1, 0, false, "unusedPush")
            val reflected = driver.reflect(artifact(bindings = listOf(binding), pushes = listOf(member)))
            reflected.validate(null, VertexState.Empty)
            assertTrue(reflected.bindings.isEmpty())
            assertTrue(reflected.pushConstants.isEmpty())
        }
    }

    @Test
    fun undeclaredUniformsAndSamplerTypeArrayOrLocationMismatchReleaseLinkedProgram() {
        for (variable in listOf(Variable("other", 0x8B5E), Variable("image", 0x8B62), Variable("image[0]", 0x8B5E, 3), Variable("image", 0x8B5E))) {
            InterfaceDriver(false).use { driver ->
                driver.uniforms.add(variable)
                val binding = OpenGLShaderBinding(0, 0, 2, OpenGLShaderBindingKind.CombinedTextureSampler, 0, "image")
                assertFailsWith<IllegalArgumentException> { driver.link(artifact(bindings = listOf(binding))) }
                assertEquals(listOf(1), driver.deletedPrograms)
            }
        }
    }

    @Test
    fun realBlockSizeAndStageAccessAreSeparateFromMinimumExposedBufferRange() {
        InterfaceDriver(true).use { driver ->
            driver.block()
            val reflected = driver.reflect(artifact(bindings = listOf(blockBinding())))
            val layout = driver.device.createPipelineLayout(blockLayout(65536)) as OpenGLPipelineLayout
            try {
                reflected.validate(layout.description, VertexState.Empty)
                assertEquals(80, reflected.bindings.single().requiredSizeBytes)
                assertEquals(3, reflected.bindings.single().block.value)
                assertEquals(setOf(ShaderStage.Vertex), reflected.bindings.single().stages)
                assertEquals(listOf(
                    "program:35718", "program:35719", "uniform:0", "uniform:1", "properties:35386:0,1", "blockIndex:scene",
                    "block:3:35392", "block:3:35396", "block:3:35398",
                    "properties:35387:0", "properties:35388:0", "properties:35389:0", "properties:35390:0",
                    "properties:35387:1", "properties:35388:1", "properties:35389:1", "properties:35390:1", "program:35721",
                ), driver.linkQueries)
            } finally {
                layout.close()
            }
        }
    }

    @Test
    fun anonymousAndLegacyBlockMemberNamesRetainTheSameTypedByteContract() {
        for (anonymous in listOf(false, true)) for (blockName in listOf("scene", "scene.with_dot")) {
            InterfaceDriver(true).use { driver ->
                driver.block()
                driver.blockIndices.clear()
                driver.blockIndices[blockName] = 3
                driver.uniforms.indices.forEach { ordinal ->
                    val name = if (anonymous) "${blockName}_member_$ordinal" else "${blockName}.member_$ordinal"
                    driver.uniforms[ordinal] = driver.uniforms[ordinal].copy(name = name)
                }
                driver.fragmentBlock = true
                val binding = blockBinding().copy(name = blockName)
                val reflected = driver.reflect(
                    artifact(bindings = listOf(binding)),
                    artifact(ShaderStage.Fragment, bindings = listOf(binding)),
                )
                val selected = reflected.bindings.single()
                assertEquals(binding, selected.declaration)
                assertEquals(3, selected.block.value)
                assertEquals(80, selected.requiredSizeBytes)
                assertEquals(setOf(ShaderStage.Vertex, ShaderStage.Fragment), selected.stages)
            }
        }
    }

    @Test
    fun anonymousMembersRemainUniqueAcrossMatchedNativeBlocks() {
        InterfaceDriver(true).use { driver ->
            driver.block()
            driver.uniforms.indices.forEach { ordinal ->
                driver.uniforms[ordinal] = driver.uniforms[ordinal].copy(name = "scene_member_$ordinal")
            }
            driver.blockIndices["other"] = 7
            driver.uniforms.addAll(listOf(
                Variable(
                    "other_member_0",
                    0x8B52,
                    block = 7,
                ),
                Variable(
                    "other_member_1",
                    0x8B5C,
                    block = 7,
                    offset = 16,
                    matrixStride = 16,
                ),
            ))
            val first = blockBinding()
            val second = first.copy(set = 2, binding = 3, name = "other")
            val reflected = driver.reflect(artifact(bindings = listOf(first, second)))
            assertEquals(listOf(first, second), reflected.bindings.map { it.declaration })
            assertEquals(listOf(3, 7), reflected.bindings.map { it.block.value })
        }
    }

    @Test
    fun bareForeignSuffixAndDuplicateOrdinalBlockAliasesAreRejected() {
        for (name in listOf("member_0", "other_member_0", "other.member_0", "scene_member_2", "scene_member_0[0]", "prefix_scene_member_0", "scene.member_0.suffix")) {
            InterfaceDriver(true).use { driver ->
                driver.block()
                driver.blockIndices["other"] = 7
                driver.uniforms[0] = driver.uniforms[0].copy(name = name)
                assertFailsWith<IllegalArgumentException> { driver.link(artifact(bindings = listOf(blockBinding()))) }
                assertEquals(listOf(1), driver.deletedPrograms)
            }
        }
        InterfaceDriver(true).use { driver ->
            driver.block()
            driver.uniforms.add(driver.uniforms[0].copy(name = "scene_member_0"))
            assertFailsWith<IllegalArgumentException> { driver.link(artifact(bindings = listOf(blockBinding()))) }
            assertEquals(listOf(1), driver.deletedPrograms)
        }
        InterfaceDriver(true).use { driver ->
            driver.block()
            val binding = blockBinding().copy(name = "scene.with_dot")
            driver.blockIndices.clear()
            driver.blockIndices[binding.name] = 3
            driver.uniforms[0] = driver.uniforms[0].copy(name = "scene_with_dot_member_0")
            assertFailsWith<IllegalArgumentException> { driver.link(artifact(bindings = listOf(binding))) }
        }
    }

    @Test
    fun anonymousNamingPreservesEveryNativeLayoutAndUndeclaredUniformRejection() {
        for (change in listOf<(InterfaceDriver) -> Unit>(
            { it.uniforms[0] = it.uniforms[0].copy(type = 0x1406) },
            { it.uniforms[0] = it.uniforms[0].copy(count = 2) },
            { it.uniforms[0] = it.uniforms[0].copy(offset = 4) },
            { it.uniforms[0] = it.uniforms[0].copy(arrayStride = 16) },
            { it.uniforms[1] = it.uniforms[1].copy(matrixStride = 32) },
            { it.uniforms[1] = it.uniforms[1].copy(rowMajor = true) },
            { it.uniforms[0] = it.uniforms[0].copy(block = 7) },
            { it.blockSize = 79 },
            { it.fragmentBlock = true },
            { it.uniforms.add(Variable("undeclared", 0x1406)) },
            { it.uniforms.add(Variable("foreign_member_0", 0x8B52, block = 7)) },
        )) {
            InterfaceDriver(true).use { driver ->
                driver.block()
                driver.uniforms.indices.forEach { ordinal ->
                    driver.uniforms[ordinal] = driver.uniforms[ordinal].copy(name = "scene_member_$ordinal")
                }
                change(driver)
                assertFailsWith<IllegalArgumentException> { driver.link(artifact(bindings = listOf(blockBinding()))) }
                assertEquals(listOf(1), driver.deletedPrograms)
            }
        }
    }

    @Test
    fun nativeBlockOffsetsStridesRowMajorSizeAndStageMismatchAreRejected() {
        for (change in listOf<(InterfaceDriver) -> Unit>(
            { it.uniforms[0] = it.uniforms[0].copy(offset = 4) },
            { it.uniforms[1] = it.uniforms[1].copy(matrixStride = 32) },
            { it.uniforms[1] = it.uniforms[1].copy(rowMajor = true) },
            { it.uniforms[0] = it.uniforms[0].copy(arrayStride = 16) },
            { it.blockSize = 79 },
            { it.fragmentBlock = true },
        )) {
            InterfaceDriver(true).use { driver ->
                driver.block()
                change(driver)
                assertFailsWith<IllegalArgumentException> { driver.link(artifact(bindings = listOf(blockBinding()))) }
                assertEquals(listOf(1), driver.deletedPrograms)
            }
        }
    }

    @Test
    fun undeclaredBlockAndAbsentUboCapabilityCannotBeSilentlyIgnored() {
        InterfaceDriver(true).use { driver ->
            driver.block()
            assertFailsWith<IllegalArgumentException> { driver.link(artifact()) }
        }
        InterfaceDriver(false).use { driver ->
            assertFailsWith<UnsupportedOperationException> { driver.link(artifact(bindings = listOf(blockBinding()))) }
            assertTrue(driver.queries.none { it.startsWith("properties:") })
        }
    }

    @Test
    fun optimizedOutBlockCanHaveNoRequiredSetButStillNeedsItsRepresentationCapability() {
        InterfaceDriver(true).use { driver ->
            val reflected = driver.reflect(artifact(bindings = listOf(blockBinding())))
            reflected.validate(null, VertexState.Empty)
            assertTrue(reflected.requiredSets.isEmpty())
        }
    }

    @Test
    fun artifactStagesMustAgreeAboutLogicalResourceTypesCountsAndMemberLayouts() {
        for (different in listOf(blockBinding().copy(count = 2), blockBinding().copy(blockSignature = "0:3:1:0:false"), blockBinding().copy(kind = OpenGLShaderBindingKind.CombinedTextureSampler))) {
            InterfaceDriver(true).use { driver ->
                assertFailsWith<IllegalArgumentException> { driver.link(artifact(bindings = listOf(blockBinding())), artifact(ShaderStage.Fragment, bindings = listOf(different))) }
            }
        }
    }

    @Test
    fun nativeVertexLocationsAndTypesMustMatchArtifactBeforeVertexStateValidation() {
        InterfaceDriver(false).use { driver ->
            val input = OpenGLShaderInput(7, 2, "coordinate")
            driver.attributes.add(Variable(input.name, 0x8B50))
            driver.attributeLocations[input.name] = 7
            val reflected = driver.reflect(artifact(inputs = listOf(input)))
            reflected.validate(null, VertexState(listOf(VertexBufferLayout(2, VertexStepMode.Vertex, listOf(VertexAttribute(7, VertexFormat.Uint8x2Normalized, 0))))))
            reflected.validate(null, VertexState(listOf(VertexBufferLayout(16, VertexStepMode.Vertex, listOf(VertexAttribute(7, VertexFormat.Float32x4, 0))))))
            assertFailsWith<IllegalArgumentException> { reflected.validate(null, VertexState.Empty) }
            assertFailsWith<IllegalArgumentException> { reflected.validate(null, VertexState(listOf(VertexBufferLayout(8, VertexStepMode.Vertex, listOf(VertexAttribute(7, VertexFormat.Uint32x2, 0)))))) }
        }
        for (location in listOf(6, -1)) {
            InterfaceDriver(false).use { driver ->
                driver.attributes.add(Variable("coordinate", 0x8B50))
                driver.attributeLocations["coordinate"] = location
                assertFailsWith<IllegalArgumentException> { driver.link(artifact(inputs = listOf(OpenGLShaderInput(7, 2, "coordinate")))) }
            }
        }
        InterfaceDriver(false).use { driver ->
            driver.attributes.add(Variable("undeclared", 0x8B50))
            assertFailsWith<IllegalArgumentException> { driver.link(artifact()) }
        }
    }

    @Test
    fun nativeReflectionErrorsReleaseProgramAndPreserveBorrowedModules() {
        InterfaceDriver(false).use { driver ->
            driver.errorOnQuery = "program:35718"
            assertFailsWith<OpenGLOperationException> { driver.link(artifact()) }
            assertEquals(listOf(1), driver.deletedPrograms)
            assertTrue(driver.deletedShaders.isEmpty())
        }
    }

    @Test
    fun metadataFreeProgramsRemainNativeShadersButCannotClaimLogicalInterfaceCompatibility() {
        InterfaceDriver(false).use { driver ->
            val raw = driver.device.createShaderModule(ShaderModuleDescription(ShaderStage.Vertex, ShaderSource(ShaderLanguage.Glsl, "#version 120\nvoid main() {}", "raw")))
            try {
                val stages = driver.device.createShaderStages(ShaderStagesDescription(listOf(raw), "raw")) as OpenGLShaderStages
                try {
                    assertFailsWith<UnsupportedOperationException> { context(driver.device) { stages.validateInterface(null, VertexState.Empty) } }
                } finally {
                    stages.close()
                }
            } finally {
                raw.close()
            }
        }
    }

    @Test
    fun samplerArrayCanHaveOptimizedOutElementLocationsWithoutRenumbering() {
        InterfaceDriver(false).use { driver ->
            val binding = OpenGLShaderBinding(0, 3, 2, OpenGLShaderBindingKind.CombinedTextureSampler, 0, "images")
            driver.uniforms.add(Variable("images[0]", 0x8B5E, 2))
            driver.locations["images[1]"] = 17
            val reflected = driver.reflect(artifact(bindings = listOf(binding)))
            assertEquals(listOf(-1, 17), reflected.bindings.single().locations.map { it.value })
        }
    }

    @Test
    fun coreProfileUsesPreparedCoreSourceWhilePushStillUsesOrdinaryUniforms() {
        InterfaceDriver(false, legacy = false).use { driver ->
            val member = OpenGLPushConstantMember(0, 1, 1, 0, false, "threshold")
            driver.uniforms.add(Variable(member.name, 0x1406))
            driver.locations[member.name] = 3
            val reflected = driver.reflect(artifact(ShaderStage.Fragment, pushes = listOf(member)))
            assertTrue(driver.sources.single().startsWith("#version 140"))
            assertEquals(3, reflected.pushConstants.single().location.value)
            assertTrue(driver.queries.none { it.startsWith("properties:") })
        }
    }

    @Test
    fun overlappingPushBytesKeepDistinctStageUniformSelections() {
        InterfaceDriver(false).use { driver ->
            val vertex = OpenGLPushConstantMember(48, 4, 1, 0, false, "vertexColor")
            val fragment = vertex.copy(name = "fragmentColor")
            driver.uniforms.addAll(listOf(Variable(vertex.name, 0x8B52), Variable(fragment.name, 0x8B52)))
            driver.locations.putAll(mapOf(vertex.name to 3, fragment.name to 9))
            val reflected = driver.reflect(artifact(pushes = listOf(vertex)), artifact(ShaderStage.Fragment, pushes = listOf(fragment)))
            val description = PipelineLayoutDescription(pushConstants = PushConstantLayout(listOf(PushConstantRange(setOf(ShaderStage.Vertex, ShaderStage.Fragment), 48, 16))), label = "overlap")
            reflected.validate(description, VertexState.Empty)
            assertEquals(listOf(ShaderStage.Vertex, ShaderStage.Fragment), reflected.pushConstants.map { it.stage })
            assertEquals(listOf(3, 9), reflected.pushConstants.map { it.location.value })
        }
    }

    @Test
    fun interfaceUseChecksLayoutDeviceClosureAndBorrowedShaderLifetime() {
        InterfaceDriver(false).use { driver ->
            val stages = driver.link(artifact())
            val layout = driver.device.createPipelineLayout(PipelineLayoutDescription(label = "owned")) as OpenGLPipelineLayout
            layout.close()
            assertFailsWith<IllegalStateException> { context(driver.device) { stages.validateInterface(layout, VertexState.Empty) } }
            InterfaceDriver(false).use { other ->
                val foreign = other.device.createPipelineLayout(PipelineLayoutDescription(label = "foreign")) as OpenGLPipelineLayout
                try {
                    assertFailsWith<IllegalArgumentException> { context(driver.device) { stages.validateInterface(foreign, VertexState.Empty) } }
                } finally {
                    foreign.close()
                }
            }
            driver.closeModules()
            assertFailsWith<IllegalStateException> { context(driver.device) { stages.requireInterface() } }
        }
    }

    @Test
    fun artifactProgramsCannotMixInStagesWithoutLogicalMetadata() {
        InterfaceDriver(false).use { driver ->
            val raw = driver.device.createShaderModule(ShaderModuleDescription(ShaderStage.Fragment, ShaderSource(ShaderLanguage.Glsl, "#version 120\nvoid main() {}", "raw")))
            val prepared = driver.device.createShaderModule(ShaderModuleDescription(ShaderStage.Vertex, ShaderBinary.copyOf(ByteBuffer.wrap(artifact().encode()), ShaderBinaryFormat.OpenGLGlsl, "artifact")))
            try {
                assertFailsWith<IllegalArgumentException> { driver.device.createShaderStages(ShaderStagesDescription(listOf(prepared, raw), "mixed metadata")) }
                assertEquals(listOf(1), driver.deletedPrograms)
                assertTrue(driver.deletedShaders.isEmpty())
            } finally {
                prepared.close()
                raw.close()
            }
        }
    }

    private fun artifact(stage: ShaderStage = ShaderStage.Vertex, bindings: List<OpenGLShaderBinding> = emptyList(), pushes: List<OpenGLPushConstantMember> = emptyList(), inputs: List<OpenGLShaderInput> = emptyList()): OpenGLShaderArtifact = OpenGLShaderArtifact(
        stage,
        120,
        "#version 120\nvoid main() {}",
        "#version 140\nvoid main() {}",
        inputs,
        emptyList(),
        bindings,
        pushes,
    )

    private fun blockBinding(): OpenGLShaderBinding = OpenGLShaderBinding(
        0,
        9,
        1,
        OpenGLShaderBindingKind.UniformBuffer,
        80,
        "scene",
        "0:4:1:0:false;16:4:4:16:false",
    )

    private fun blockLayout(minimumBytes: Long): PipelineLayoutDescription = PipelineLayoutDescription(
        listOf(DescriptorSetLayout(listOf(DescriptorBindingLayout(9, DescriptorType.UniformBuffer(minimumBytes), setOf(ShaderStage.Vertex))))),
        label = "range",
    )
}

private data class Variable(
    val name: String,
    val type: Int,
    val count: Int = 1,
    val block: Int = -1,
    val offset: Int = 0,
    val arrayStride: Int = 0,
    val matrixStride: Int = 0,
    val rowMajor: Boolean = false,
)

private class InterfaceDriver(hasUbo: Boolean, legacy: Boolean = true) : AutoCloseable {
    val queries = mutableListOf<String>()
    var linkQueries = emptyList<String>()
    val uniforms = mutableListOf<Variable>()
    val attributes = mutableListOf<Variable>()
    val locations = mutableMapOf<String, Int>()
    val attributeLocations = mutableMapOf<String, Int>()
    val blockIndices = mutableMapOf<String, Int>()
    var blockSize = 80
    var fragmentBlock = false
    var components = 1024
    var errorOnQuery: String? = null
    private var error = 0
    val sources = mutableListOf<String>()
    val deletedPrograms = mutableListOf<Int>()
    val deletedShaders = mutableListOf<Int>()
    private val modules = mutableListOf<ShaderModule>()
    private val stages = mutableListOf<ShaderStages>()
    private var shader = 0
    private var program = 0
    private fun query(value: String) { queries.add(value); if (errorOnQuery == value) error = 0x0502 }
    private val ubo = interfaceProxy<OpenGLUniformBufferFunctions> { name, args ->
        when (name) {
            "getMaximumBindings" -> 24
            "getUniformBlockIndex" -> { query("blockIndex:${args[1]}"); blockIndices[args[1].toString()] ?: -1 }
            "getUniformProperties" -> {
                val indices = args[1] as IntBuffer
                val parameter = args[2] as Int
                val propertyIndices = (indices.position() until indices.limit()).joinToString(",") { indices[it].toString() }
                query("properties:$parameter:$propertyIndices")
                val destination = args[3] as IntBuffer
                for (index in indices.position() until indices.limit()) {
                    val variable = uniforms[indices[index]]
                    destination.put(index - indices.position(), when (parameter) {
                        0x8A3A -> variable.block
                        0x8A3B -> variable.offset
                        0x8A3C -> variable.arrayStride
                        0x8A3D -> variable.matrixStride
                        0x8A3E -> if (variable.rowMajor) 1 else 0
                        else -> error("Unexpected uniform property")
                    })
                }
                null
            }
            "getUniformBlockProperties" -> {
                query("block:${args[1]}:${args[2]}")
                (args[3] as IntBuffer).put(0, when (args[2]) { 0x8A40 -> blockSize; 0x8A44 -> 1; 0x8A46 -> if (fragmentBlock) 1 else 0; else -> error("Unexpected block property") })
                null
            }
            else -> error("Unexpected native UBO mutation: $name")
        }
    }
    private val framebuffers = interfaceProxy<OpenGLFramebufferFunctions> { name, _ -> error("Unexpected framebuffer call: $name") }
    private val functions = interfaceProxy<OpenGLFunctions> { name, args ->
        when (name) {
            "checkCurrentContext", "compileShader", "attachShader", "bindVertexAttributeLocation", "linkProgram" -> null
            "getFramebuffers" -> framebuffers
            "getUniformBuffers" -> if (hasUbo) ubo else null
            "getSupportsLegacyPixelTransfer" -> legacy
            "getString" -> if (legacy) "1.20" else "1.40"
            "getError" -> error.also { error = 0 }
            "createShader" -> ++shader
            "createProgram" -> ++program
            "getShaderCompileStatus", "getProgramLinkStatus" -> true
            "shaderSource" -> { sources.add(args[1].toString()); null }
            "deleteShader" -> { deletedShaders.add(args[0] as Int); null }
            "deleteProgram" -> { deletedPrograms.add(args[0] as Int); null }
            "getInteger" -> { query("integer:${args[0]}"); if (args[0] == 0x8B4A || args[0] == 0x8B49) components else 24 }
            "getProgramInteger" -> {
                query("program:${args[1]}")
                when (args[1]) {
                    0x8B86 -> uniforms.size
                    0x8B89 -> attributes.size
                    0x8B87 -> uniforms.maxOf { it.name.length } + 1
                    0x8B8A -> attributes.maxOf { it.name.length } + 1
                    else -> error("Unexpected program parameter")
                }
            }
            "getActiveUniform", "getActiveVertexAttribute" -> {
                val index = args[1] as Int
                query(if (name == "getActiveUniform") "uniform:$index" else "attribute:$index")
                val variable = (if (name == "getActiveUniform") uniforms else attributes)[index]
                val bytes = variable.name.toByteArray(Charsets.UTF_8)
                (args[2] as IntBuffer).put(0, bytes.size)
                (args[3] as IntBuffer).put(0, variable.count)
                (args[4] as IntBuffer).put(0, variable.type)
                val destination = args[5] as ByteBuffer
                bytes.forEachIndexed { offset, value -> destination.put(offset, value) }
                null
            }
            "getUniformLocation" -> { query("location:${args[1]}"); locations[args[1].toString()] ?: -1 }
            "getVertexAttributeLocation" -> { query("attributeLocation:${args[1]}"); attributeLocations[args[1].toString()] ?: -1 }
            else -> error("Unexpected native mutation: $name")
        }
    }
    val device = OpenGLGraphicsDevice(functions, canonicalShaderCompiler = RecordingCompiler())

    fun block() {
        blockIndices["scene"] = 3
        uniforms.add(Variable("scene.member_0", 0x8B52, block = 3))
        uniforms.add(Variable("scene.member_1", 0x8B5C, block = 3, offset = 16, matrixStride = 16))
    }

    fun link(vararg artifacts: OpenGLShaderArtifact): OpenGLShaderStages {
        val created = artifacts.map { artifact -> device.createShaderModule(ShaderModuleDescription(artifact.stage, ShaderBinary.copyOf(ByteBuffer.wrap(artifact.encode()), ShaderBinaryFormat.OpenGLGlsl, "fixture"))).also { modules.add(it) } }
        queries.clear()
        val result = device.createShaderStages(ShaderStagesDescription(created, "reflected")) as OpenGLShaderStages
        stages.add(result)
        linkQueries = queries.toList()
        return result
    }

    fun reflect(vararg artifacts: OpenGLShaderArtifact): OpenGLShaderInterface {
        val linked = link(*artifacts)
        return context(device) { linked.requireInterface() }
    }

    fun closeModules() { modules.asReversed().forEach { it.close() } }

    override fun close() = terminateOnFailure {
        stages.asReversed().forEach { it.close() }
        modules.asReversed().forEach { it.close() }
        device.close()
    }
}

private inline fun <reified T> interfaceProxy(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments -> invoke(method.name.substringBefore('-'), arguments ?: emptyArray()) } as T
