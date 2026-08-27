# Contributing

Thank you for contributing to this project.

## Comments & Documentation

Code is the primary way to express program structure, behavior, and intent. Prefer clear names, types, structure, and
abstraction boundaries over comments that compensate for unclear code.

Comments and documentation should add context that the code cannot communicate clearly or efficiently on its own. Do not
mechanically restate declarations, identifiers, or execution steps.

Documentation is not required merely because something is public, complex-looking, or otherwise appears to "need
comments". Likewise, comments should not be added to satisfy a documentation quota. If the code already communicates
everything a reader needs to know, additional prose may only add noise and another source of information that can become
outdated.

This does not mean that documentation should contain only facts that are impossible to derive from the implementation. A
reader should not need to reconstruct an entire implementation before understanding the concept it represents.
Plain-language explanations are useful when they communicate a mental model, purpose, or relationship more clearly than
the code can.

For example, this comment adds no information:

```kotlin
// Increment the retry count.
retryCount++
```

If the operation itself is difficult to understand, improve the code rather than narrating it.

By contrast:

```kotlin
// Count the initial attempt so the configured limit represents
// the total number of attempts, not only retries.
attemptCount++
```

adds a reason that is not apparent from the statement itself.

The important question is not whether information can theoretically be recovered from the implementation, but whether
the code communicates it clearly and efficiently on its own.

### Explain the concept before the mechanics

Documentation should help readers understand not only *what* something is, but also *why it exists*, *what problem it
solves*, and *when it matters*.

Do not assume that contributors already know the surrounding terminology or concepts. When something represents a
non-trivial idea, begin with a simple and accurate mental model before introducing detailed processing rules, formulas,
edge cases, or implementation details.

For example:

```kotlin
/**
 * Stores cached values.
 */
class Cache
```

describes the structure, but gives a reader little reason to care about it.

A more useful introduction would be:

```kotlin
/**
 * Reuses previously computed values so repeated requests can avoid
 * performing the same expensive work again.
 *
 * Entries may disappear when they expire or when space needs to be reclaimed.
 */
class Cache
```

The implementation may make these facts discoverable, but a reader should not need to inspect lookup and eviction logic
merely to understand what role the abstraction serves.

Useful documentation often answers questions such as:

- What does this concept represent?
- Why does it exist?
- What problem does it solve?
- When would I use, extend, or modify it?
- How does it relate to nearby concepts or alternatives?

Do not begin with an exhaustive execution specification when a plain-language explanation would establish the concept
more effectively.

For example, an exponential backoff policy should usually not begin with:

```text
delay(attempt) = min(maxDelay, initialDelay * 2^attempt)
```

before explaining that retries become progressively less frequent to avoid repeatedly overwhelming a failing service.

Establish the idea first. Precise formulas and processing rules can follow once the reader knows what they describe and
why they matter.

### Document what the code cannot express well

After establishing the concept, document the non-obvious information needed to use or modify it correctly.

When relevant, explain:

- caller-visible behavior that names, types, and structure do not make clear or enforce;
- invariants and ordering constraints;
- interactions with other state;
- surprising edge cases and invalid combinations;
- ownership, lifetime, and invalidation rules;
- failure behavior and recovery expectations;
- thread-safety and synchronization requirements;
- significant performance characteristics or resource costs;
- important tradeoffs or reasons for choosing one design over nearby alternatives.

For example, this declaration does not communicate the lifetime of its result:

```kotlin
fun currentText(): CharSequence
```

If the returned value refers to reusable internal storage, that should be documented:

```kotlin
/**
 * Returns a view of the current text.
 *
 * The returned value is backed by internal storage and remains valid
 * only until the next call to parse().
 */
fun currentText(): CharSequence
```

Ordering guarantees are another common example:

```kotlin
/**
 * Submits a task for execution.
 *
 * Tasks submitted by the same producer are processed in submission order.
 * No ordering is guaranteed between different producers.
 */
fun submit(task: Task)
```

The method name already tells the reader *what operation is being requested*. The documentation adds the contract
required to reason about its behavior.

Documentation should remain proportional to the concept. Simple declarations should stay concise, while complex or
easily misunderstood behavior deserves more detail.

For example, if this property has only one non-obvious special case:

```kotlin
val maxRetries: Int
```

then this may be all the documentation it needs:

```kotlin
/**
 * Maximum number of retries. `0` disables retries.
 */
val maxRetries: Int
```

Do not invent filler use cases, edge cases, parameter descriptions, or other prose merely to make documentation appear
more complete.

Use examples, diagrams, tables, or formulas when they communicate behavior more clearly than prose alone. In formulas
and examples, prefer complete and descriptive names over unexplained abbreviations.

### Comments

Implementation comments should primarily explain *why* the code is written a particular way when that reason is not
apparent from the code itself.

Useful comments commonly record:

- a non-obvious invariant;
- why an apparently simpler implementation would be incorrect;
- an external constraint or workaround;
- a concurrency, lifetime, or ordering requirement;
- a deliberate performance tradeoff;
- code whose necessity could otherwise be mistaken for accidental complexity.

Avoid comments that merely narrate the next statement:

```kotlin
// Remove the entry.
entries.remove(key)
```

Instead, explain the reason when it matters:

```kotlin
// Remove the listener before closing the connection.
// close() may synchronously invoke the disconnect callback.
connection.removeListener(listener)
connection.close()
```

Likewise:

```kotlin
// Acquire the lock.
lock.lock()
```

adds nothing, while:

```kotlin
// Keep the lock held while publishing the metadata so readers
// cannot observe the new value with stale metadata.
lock.lock()
```

records an invariant that might otherwise be lost during refactoring.

If a comment exists only because the code is difficult to read, prefer improving the code when practical.

Prefer:

```kotlin
val hasExpired = now >= expirationTime

if (hasExpired) {
    removeEntry()
}
```

over:

```kotlin
// Check whether the current time is past the expiration time.
val result = now >= end

// Remove the entry if it has expired.
if (result) {
    removeEntry()
}
```

Comments should explain necessary complexity, not preserve avoidable complexity.

In short:

> Code should communicate what it reasonably can. Comments and documentation should provide the context that code cannot
> communicate clearly on its own.
## Mixin Rules

### Do not directly implement interfaces

A Mixin type must not use ordinary `implements` to add an interface to its target class.

```java

@Mixin(TargetClass.class)
abstract class TargetClassMixin implements ProjectInterface {
}
```

This couples the interface design to Mixin implementation details and may require prefixed bridge methods to be declared
or exposed in places where they do not belong.

Use `@Implements` when the interface is implemented through Mixin:

```java

@Mixin(TargetClass.class)
@Implements(@Interface(
        iface = ProjectInterface.class,
        prefix = "matrix$"
))
abstract class TargetClassMixin {
    public void matrix$projectMethod() {
    }
}
```

The prefix belongs only to the Mixin implementation and must not be added to the interface itself.

When the target class should genuinely implement the interface as part of its type hierarchy, use ClassTweaker or an
equivalent class transformation tool to add the interface directly.

Direct `implements` is only acceptable when the Mixin interface is required by Mixin itself or by another external
framework and is not intended to become an interface of the target class.

### Prefer access transformations over Accessor Mixins

Do not use `@Accessor` or `@Invoker` merely to access a member whose visibility can be changed using ClassTweaker, an
access widener, or an equivalent transformation tool.

Avoid:

```java

@Mixin(TargetClass.class)
interface TargetClassAccessor {
    @Accessor("value")
    Object getValue();
}
```

when the intended result is simply to make the original member accessible.

Accessor and Invoker Mixins introduce a separate bridge API, require callers to cast to an artificial type, and make the
source-visible API differ from the actual runtime API. A direct access transformation keeps the original member as the
single API.

Accessor or Invoker Mixins may be used when:

- the required operation cannot be expressed by an access transformation;
- widening the original member would expose it more broadly than intended;
- the relevant platform does not support an equivalent transformation;
- compatibility with external code specifically requires an Accessor or Invoker.

The reason for using an exception must be documented near the Mixin.

## Resource cleanup and unrecoverable failures

Project-defined `AutoCloseable` implementations must treat `close()` as a fatal-boundary destructor.

This rule applies to project-owned resource types. Third-party `AutoCloseable` implementations may define their own
failure contracts.

### `close()` must not expose failures to callers

Every project-defined `close()` implementation must wrap its complete cleanup body in `terminateOnFailure`:

```kotlin
override fun close() = runOrTerminate {
    releaseResource()
}
```

A cleanup failure is not a recoverable operational error. If resource destruction fails, the process must terminate
immediately. No exception may escape from `close()`.

The following implementation is prohibited:

```kotlin
override fun close() {
    releaseResource()
}
```

It is also prohibited to catch a cleanup failure, attach it as a suppressed exception, and continue propagating the
original exception:

```kotlin
try {
    operation()
} catch (failure: Throwable) {
    try {
        close()
    } catch (cleanupFailure: Throwable) {
        failure.addSuppressed(cleanupFailure)
    }

    throw failure
}
```

A failed destructor means that the resource-lifetime invariant can no longer be proven. The caller cannot restore
ownership, validity, or consistency after destruction has already failed. Continuing execution in this state is unsafe.

Do not catch an exception merely because an operation can theoretically throw. Catch it only when the current layer has
a defined recovery strategy that preserves program invariants.

### Do not hide fallible operations inside `close()`

`close()` must perform only final resource release.

Operations with meaningful failure semantics must be exposed explicitly:

```kotlin
archive.finish()
writer.flush()
transaction.commit()
```

Such operations may fail because the caller can define a valid response, such as retrying, aborting the current
operation, reporting the failure, or selecting an alternative.

After an explicit operation succeeds or fails, `close()` must still release the underlying resource without propagating
another exception.

Do not hide commit, flush, protocol completion, synchronization, or other caller-visible operations inside `close()`.

### Why this rule exists

Resource destruction is fundamentally different from an ordinary operational failure.

An operational failure may be propagated when the program remains in a valid state and the caller can take meaningful
action.

A failed destructor does not provide such a recovery path. It commonly indicates one of the following:

- broken resource ownership;
- an invalid or already released handle;
- invalid native state;
- destruction from the wrong thread or graphics context;
- violation of a resource-lifetime invariant;
- another programming error in cleanup code.

An upper layer cannot repair these conditions merely by catching an exception. Propagating the failure only permits
execution to continue in a state whose validity can no longer be established.

Therefore:

- recoverable operational failures may be returned or thrown;
- explicit commit, flush, finish, or synchronization operations may fail;
- project-owned destructors must not propagate failures;
- a destructor failure is fatal and must terminate the process;
- callers must not catch, suppress, ignore, or retry destructor failures;
- `addSuppressed` must not be used to disguise failed cleanup as recoverable;
- catching `Throwable` is restricted to the centralized `terminateOnFailure` implementation, where the only valid action
  is
  immediate termination.

`terminateOnFailure` uses `Runtime.halt`, bypassing normal exception propagation and shutdown hooks. Once the fatal
boundary
is entered, control must never return to ordinary execution.