package heckerpowered.lethal.bridge.platform.services

interface ModPlatform {
    /**
     * The name of current platform, e.g. "Fabric", "Forge", "Paper", etc.
     */
    val platformName: String

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return `true` if the mod is loaded, `false` otherwise.
     */
    fun isModLoaded(modId: String): Boolean

    /**
     * Check if the game is currently in a development environment.
     */
    val isDevelopmentEnvironment: Boolean

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    val environmentName: String
        get() = if (isDevelopmentEnvironment) "development" else "production"
}