/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

object ItemRegistry {
    private val blueprintsByIdentifier = LinkedHashMap<String, ItemBlueprint>()

    fun <T : ItemBlueprint> register(blueprint: T): T {
        val identifier = blueprint.identifier.asString()
        require(identifier !in blueprintsByIdentifier) { "Item identifier has already been registered: $identifier" }

        blueprintsByIdentifier[identifier] = blueprint
        return blueprint
    }

    fun all(): List<ItemBlueprint> {
        return blueprintsByIdentifier.values.toList()
    }

    // TODO: Delete this. Production registries are not test fixtures.
    //  This method exists only so tests can mutate global production state back into a
    //  convenient shape after each run. That is not lifecycle design; it is test code
    //  forcing a reset button into the real API because the tests were built around
    //  shared mutable state.
    //  Tests must create a fresh registry, replace the dependency with a test-owned
    //  instance, or isolate the global state at the test boundary. They must not invent
    //  destructive production operations that have no legitimate runtime caller.
    //  Stop designing production APIs around teardown convenience. If no production
    //  behavior requires this registry to forget every blueprint at once, clear() has no
    //  business existing here.
    internal fun clear() {
        blueprintsByIdentifier.clear()
    }
}
