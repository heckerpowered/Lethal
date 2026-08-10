# Contributing

Thank you for contributing to this project.

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