/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.core

import net.minecraft.launchwrapper.Launch
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.*
import org.apache.commons.lang3.reflect.MethodUtils
import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.mixin.Mixins
import org.spongepowered.asm.service.MixinService

@Name("LethalCoreMod")
@MCVersion("1.12.2")
@SortingIndex(value = Integer.MIN_VALUE)
class CorePlugin : IFMLLoadingPlugin {
    companion object {
        @JvmStatic
        fun launchMixin() {
            MixinBootstrap.init()
            Mixins.addConfiguration("mixins.lethal.json")

            MixinService.getService()
                .transformerProvider
                .addTransformerExclusion(
                    "wiresegal.thicc.asm.ThiccAsmTransformer",
                )
        }
    }

    /**
     * Securely launch mixin, due to mixin cannot be initialized directly here, plugins and mixin are loaded by
     * different class loaders. There may be side effects. If problems occur, try switching to the Tweaker Mod to
     * boot Mixin!
     */
    fun secureLaunchMixin() {
        val applicationClassLoader = Launch::class.java.classLoader
        MethodUtils.invokeMethod(
            applicationClassLoader,
            true,
            "addURL",
            javaClass.protectionDomain.codeSource.location
        )
        MethodUtils.invokeStaticMethod(
            applicationClassLoader.loadClass(javaClass.name),
            "launchMixin"
        )
    }

    override fun getASMTransformerClass(): Array<out String> = emptyArray()

    override fun getModContainerClass(): String? = null

    override fun getSetupClass(): String? = null
    override fun injectData(data: Map<String, Any>) {
        secureLaunchMixin()
    }

    override fun getAccessTransformerClass(): String? = null
}