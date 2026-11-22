# Resource cleanup and unrecoverable failures

Project-defined `AutoCloseable` implementations must treat `close()` as a fatal-boundary destructor.

This rule applies to project-owned resource types. Third-party `AutoCloseable` implementations may define their own
failure contracts.

### `close()` must not expose failures to callers

Every project-defined `close()` implementation must wrap its complete cleanup body in `terminateOnFailure`:

```kotlin
override fun close() = terminateOnFailure {
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
