/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import java.nio.charset.StandardCharsets

internal class WorldLineProgram : AutoCloseable {
    private companion object {
        const val VERTEX_SHADER_PATH = "/assets/lethal/shaders/world/electric_line.vsh"
        const val FRAGMENT_SHADER_PATH = "/assets/lethal/shaders/world/electric_line.fsh"
    }

    private val programIdentifier = createProgram()
    private val uniformLocations = mutableMapOf<String, Int>()

    fun bind() {
        GL20.glUseProgram(programIdentifier)
    }

    fun setFloat(name: String, value: Float) {
        GL20.glUniform1f(uniformLocation(name), value)
    }

    fun setVector2(name: String, horizontalValue: Float, verticalValue: Float) {
        GL20.glUniform2f(uniformLocation(name), horizontalValue, verticalValue)
    }

    fun setVector3(name: String, xCoordinate: Float, yCoordinate: Float, zCoordinate: Float) {
        GL20.glUniform3f(uniformLocation(name), xCoordinate, yCoordinate, zCoordinate)
    }

    override fun close() {
        GL20.glDeleteProgram(programIdentifier)
    }

    private fun uniformLocation(name: String): Int {
        return uniformLocations.getOrPut(name) { GL20.glGetUniformLocation(programIdentifier, name) }
    }

    private fun createProgram(): Int {
        val vertexShader = compileShader(VERTEX_SHADER_PATH, GL20.GL_VERTEX_SHADER)
        val fragmentShader = try {
            compileShader(FRAGMENT_SHADER_PATH, GL20.GL_FRAGMENT_SHADER)
        } catch (throwable: Throwable) {
            GL20.glDeleteShader(vertexShader)
            throw throwable
        }

        try {
            return linkProgram(vertexShader, fragmentShader)
        } finally {
            GL20.glDeleteShader(vertexShader)
            GL20.glDeleteShader(fragmentShader)
        }
    }

    private fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GL20.glCreateProgram()
        try {
            GL20.glAttachShader(program, vertexShader)
            GL20.glAttachShader(program, fragmentShader)
            GL20.glLinkProgram(program)
            check(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_FALSE) {
                "Failed to link electric line shader program: " + GL20.glGetProgramInfoLog(program, 32768)
            }
            return program
        } catch (throwable: Throwable) {
            GL20.glDeleteProgram(program)
            throw throwable
        }
    }

    private fun compileShader(path: String, shaderType: Int): Int {
        val shaderSource = loadShaderSource(path)
        val shaderIdentifier = GL20.glCreateShader(shaderType)
        GL20.glShaderSource(shaderIdentifier, shaderSource)
        GL20.glCompileShader(shaderIdentifier)

        if (GL20.glGetShaderi(shaderIdentifier, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE) {
            return shaderIdentifier
        }

        val compilationLog = GL20.glGetShaderInfoLog(shaderIdentifier, 32768)
        GL20.glDeleteShader(shaderIdentifier)
        error("Failed to compile electric line shader $path: $compilationLog")
    }

    private fun loadShaderSource(path: String): String {
        val stream = WorldLineProgram::class.java.getResourceAsStream(path) ?: error("Electric line shader resource is missing: $path")
        return stream.bufferedReader(StandardCharsets.UTF_8).use { reader -> reader.readText() }
    }
}
