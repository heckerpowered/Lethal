/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateMemoryStackAllocations : DefaultTask() {
    @get:Input
    abstract val maximumArity: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        maximumArity.convention(16)
    }

    @TaskAction
    fun generate() {
        val arity = maximumArity.get()
        require(arity in 1..ArgumentNames.size) { "MemoryStack allocation arity must be between one and ${ArgumentNames.size}" }

        val outputFile = outputDirectory.file("heckerpowered/render/memory/MemoryStackAllocations.kt").get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(renderAllocations(arity))
    }
}

private val ArgumentNames = listOf(
    "first",
    "second",
    "third",
    "fourth",
    "fifth",
    "sixth",
    "seventh",
    "eighth",
    "ninth",
    "tenth",
    "eleventh",
    "twelfth",
    "thirteenth",
    "fourteenth",
    "fifteenth",
    "sixteenth",
)

private fun renderAllocations(maximumArity: Int): String = buildString {
    appendLine("// Generated. Do not edit.")
    appendLine("@file:Suppress(\"NOTHING_TO_INLINE\")")
    appendLine()
    appendLine("package heckerpowered.render.memory")
    appendLine()

    for (arity in 1..maximumArity) {
        renderScopedAlloc(arity)
        appendLine()
        renderFrameAlloc(arity)
        if (arity != maximumArity) appendLine()
    }
}

private fun StringBuilder.renderScopedAlloc(arity: Int) {
    val argumentNames = ArgumentNames.take(arity)
    appendLine("inline fun <R> MemoryStack.alloc(")
    argumentNames.forEach { appendLine("    $it: MemoryLayout,") }
    appendLine("    block: MemoryFrame.(${List(arity) { "NativeAddress" }.joinToString()}) -> R,")
    appendLine("): R = frame { alloc(${(argumentNames + "block").joinToString()}) }")
}

private fun StringBuilder.renderFrameAlloc(arity: Int) {
    val argumentNames = ArgumentNames.take(arity)
    appendLine("inline fun <R> MemoryFrame.alloc(")
    argumentNames.forEach { appendLine("    $it: MemoryLayout,") }
    appendLine("    block: MemoryFrame.(${List(arity) { "NativeAddress" }.joinToString()}) -> R,")
    appendLine("): R {")

    argumentNames.forEachIndexed { index, argumentName ->
        val previousEnd = if (index == 0) "pointer" else "${argumentNames[index - 1]}Address + ${argumentNames[index - 1]}.byteCount"
        appendLine("    val ${argumentName}Address = alignUp($previousEnd, $argumentName.alignment)")
    }

    val firstAddress = "${argumentNames.first()}Address"
    val lastArgument = argumentNames.last()
    appendLine("    val end = ${lastArgument}Address + $lastArgument.byteCount")
    appendLine("    check(end.rawValue in $firstAddress.rawValue..limitAddress.rawValue) { \"Memory stack overflow: requested=\${end.rawValue - pointer.rawValue} bytes, remaining=\${limitAddress.rawValue - pointer.rawValue} bytes\" }")
    appendLine("    pointer = end")
    appendLine("    return block(${argumentNames.joinToString { "${it}Address" }})")
    appendLine("}")
}
