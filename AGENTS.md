# Design Principles for AI-Generated Code

This is not a set of syntax rules to be matched mechanically. It is a set of design principles intended to help AI judge
whether code is easy to understand and modify.

Short rules are easy to misinterpret. For example, “do not use abbreviations” does not mean expanding `HTTP`; “do not
write comments” does not mean deleting API documentation; and “favor composition over inheritance” does not mean
avoiding a superclass required by a framework. Rather than attempting to enumerate every possible exception, it is
better to understand the problems these rules are trying to solve.

When applying these principles, prioritize:

1. The user’s requirements, along with correctness, safety, and compatibility.
2. Existing conventions in the current project, language ecosystem, and external APIs.
3. The design that makes intent easiest for the next reader to understand and modify.

Apply these principles only within the scope of the current task. Do not refactor unrelated code merely in pursuit of
stylistic purity.

## 1. Names Should Provide Context the Reader Does Not Already Have

Code is read far more often than it is typed. Creating abbreviations to save a few keystrokes forces every reader to
first learn a local vocabulary.

```typescript
function retry(req: Request, d: number): void {
    scheduler.schedule(req, d);
}
```

The meanings of `req` and `d` must be guessed, and `d` does not even indicate the unit of time.

```typescript
function retry(request: Request, delaySeconds: number): void {
    scheduler.schedule(request, delaySeconds);
}
```

When the language provides a unit-aware type, the type can express the meaning even more accurately than the name:

```typescript
function retry(request: Request, delay: Duration): void {
    scheduler.schedule(request, delay);
}
```

Likewise, do not repeat type information that the language already knows. `bIsVisible` is worse than `isVisible`. An
interface does not need to be named `IImage` merely because it is an interface, and a superclass does not need to be
named `BaseTruck`. The general concept can be called `Truck`, while a more specific implementation can be called
`TrailerTruck`.

Names such as `Utils` and `Helper` often indicate that behavior has no clear owner. The operations in `MovieUtils` may
actually belong to `MovieCollection`, `Pager`, and `Cookie`. However, do not eliminate such a name only to create an
equally vague wrapper class. The goal is to identify the real responsibility.

This principle applies to project-specific abbreviations that depend on local context. Widely recognized terms such as
`HTTP`, `URL`, `UUID`, and `GPU` generally do not need to be expanded. Existing ecosystem conventions and external APIs
should also remain consistent.

## 2. Let the Code Express Intent Before Adding Implementation Comments

Comments are not type-checked, and they can easily continue describing old behavior after the code changes. If a comment
merely explains what the code does, the name, structure, or type can usually express that information directly.

```typescript
// 5 means the message was sent
if (status === 5) {
    notifyRecipient();
}
```

Once a meaningful value is introduced, the code explains itself:

```typescript
if (status === MessageStatus.Sent) {
    notifyRecipient();
}
```

Types can also replace conventions that are easy to overlook. Instead of using a comment to explain that `-1` means no
timestamp is available, let the return type express the absence:

```typescript
function findTimestamp(message: Message): number | undefined {
    return message.timestamp;
}
```

This does not mean documentation should be avoided. The purpose of a public API, its thread-safety guarantees, state,
invariants, error conditions, and resource ownership often cannot be expressed entirely through implementation code and
should be documented clearly.

Internal comments are most valuable when they explain the “why” that code cannot express: measured performance
tradeoffs, mathematical formulas or paper references, defects in external systems, and non-obvious constraints that must
be preserved. Such comments should explain the reason rather than translate the implementation line by line.

## 3. Keep the Normal Path Flat

Deep nesting requires the reader to remember every outer condition before understanding the innermost code.

```typescript
function deliverMessage(message: Message | undefined): DeliveryResult {
    if (message !== undefined) {
        if (message.isValid()) {
            if (message.hasRecipient()) {
                return deliver(message);
            }
        }
    }

    return DeliveryResult.rejected();
}
```

Rejecting cases that cannot continue first allows the main behavior to be read directly:

```typescript
function deliverMessage(message: Message | undefined): DeliveryResult {
    if (message === undefined) {
        return DeliveryResult.rejected();
    }

    if (!message.isValid() || !message.hasRecipient()) {
        return DeliveryResult.rejected();
    }

    return deliver(message);
}
```

When a branch remains complex, extract it into a function with a responsibility-oriented name. A top-level function
should show the process, such as “receive requests, process downloads, remove completed items, wait for new work,”
rather than expanding every error and retry detail in place.

Around three levels of indentation can be treated as a warning signal, not an absolute limit. Tree traversal, resource
management, and transactions may naturally require nesting. Do not reduce indentation at the cost of breaking scope,
cleanup, or rollback guarantees.

## 4. Prefer Composition So That Change Remains Localized

Inheritance gives subclasses the parent’s implementation, state, and contract together. It is convenient while the
shared assumptions remain valid, but a single exception may force the entire hierarchy to change.

Suppose `Image` represents both in-memory pixels and file-related behavior:

```typescript
abstract class Image {
    public abstract load(): void;

    public abstract save(): void;

    public resize(scale: number): void {
        resizePixels(this, scale);
    }
}
```

A `DrawableImage` used only for rendering must still implement meaningless `load` and `save` methods merely to reuse
`resize`, often by throwing “unsupported” exceptions. This indicates that the parent class has bundled unrelated
responsibilities.

Once those capabilities are separated, callers can compose only what they need:

```typescript
const image = jpegFile.load();
image.resize(0.5);
imageDrawing.draw(image);
pngFile.save(image);
```

`Image` now represents only an image in memory. `jpegFile` and `pngFile` handle formats, while `imageDrawing` handles
rendering. Adding another rendering strategy does not require modifying every file type, and adding another file format
does not require changing the image itself.

Composition introduces initialization, forwarding methods, and some duplication, so it is not free. Inheritance remains
reasonable when required by a framework or plugin model, when the hierarchy is sufficiently stable, or when migration
costs greatly exceed the benefit. When designing inheritable classes, provide narrow and explicit extension points,
avoid exposing protected fields, and keep the remaining implementation private, `final`, or `sealed` where possible.

## 5. The Benefit of an Abstraction Must Exceed the Coupling It Introduces

An abstraction does more than remove duplication. It also requires multiple implementations to conform to the same
inputs, lifecycle, or method shape. Two pieces of code looking similar does not mean they should immediately share a
superclass or interface.

For example, XML and JSON savers may both accept a filename. Moving that one assignment into a common superclass removes
very little code while requiring every future saver to use files. When a database or cloud saver appears, that
abstraction becomes an obstacle.

A small amount of simple duplication is often easier to change than an incorrect abstraction. A shared interface becomes
valuable when callers genuinely need to ignore the concrete implementation:

```typescript
interface Saver {
    save(data: Data): Promise<void>;
}

class IntervalSaver {
    public constructor(private readonly saver: Saver, private readonly scheduler: IntervalScheduler) {
    }

    public start(dataSource: DataSource): void {
        this.scheduler.repeat(() => this.saver.save(dataSource.current()));
    }
}
```

Here, the scheduling logic only cares that something can save data. It should not know whether the data is written as
JSON, stored in a database, or sent to the cloud. The abstraction separates “what to choose” from “when to use it,” and
therefore provides real value.

Do not turn rules of thumb such as “abstract after three repetitions” into fixed thresholds. The relevant question is
always: what complexity does this abstraction remove now, and which concerns that could otherwise evolve independently
does it bind together?

## 6. Pass Dependencies In Rather Than Hiding Them Inside Components

When a component creates its own dependencies, it is also forced to know about concrete implementations, configuration,
and environment-specific choices.

```typescript
class AttachmentUpload {
    private readonly storage = new AmazonStorage(applicationConfiguration);

    public upload(attachment: Attachment): Promise<void> {
        return this.storage.upload(attachment);
    }
}
```

The upload process can no longer use SFTP, WebDAV, or test storage without modifying itself. Let external code make the
choice and pass in the required capability:

```typescript
class AttachmentUpload {
    public constructor(private readonly storage: AttachmentStorage) {
    }

    public upload(attachment: Attachment): Promise<void> {
        return this.storage.upload(attachment);
    }
}
```

`AttachmentUpload` now knows only that it can upload. Startup code can choose the production implementation, a request
boundary can select storage per organization, and tests can supply a controlled implementation.

Dependency injection fundamentally means passing collaborators into a component. It does not require a container.
Likewise, do not mechanically create an interface for every class. The boundary is valuable when implementations may
change, external systems need to be isolated, lifecycles differ, or tests genuinely need substitution.

If a test must modify private fields or directly invoke private methods, first check whether responsibilities have
become entangled rather than simply widening visibility.

## 7. Minimize Unnecessary State and Make Data Flow Explicit

The result of a pure function depends only on its inputs, which makes it easier to understand and test. Much business
logic is fundamentally a sequence of filtering, transformation, and sorting operations.

```typescript
const merchantNames: string[] = [];

for (const receipt of receipts) {
    if (receipt.userIdentifier === selectedUserIdentifier) {
        merchantNames.push(receipt.merchant);
    }
}
```

A data pipeline can describe the goal directly:

```typescript
const merchantNames = receipts
    .filter(receipt => receipt.userIdentifier === selectedUserIdentifier)
    .map(receipt => receipt.merchant);
```

This hides changes to the loop index and result collection, allowing the reader to focus on which receipts are kept and
which field is produced. Complex lambdas should be extracted into named functions so that every stage remains clear.

The value of functional principles lies in reducing hidden state and side effects, not in banning loops, objects, or all
state. I/O, time, randomness, and persistence inherently involve side effects. Keep them at clear boundaries while
making internal calculations as pure as practical. Do not replace a clear loop with difficult-to-read recursion, or
recursion that may overflow the call stack, merely in pursuit of purity.

## 8. Prove That a Performance Problem Exists Before Optimizing It

“Theoretically faster” often ignores the compiler, data size, memory layout, and actual workload.

Suppose there are usually only a few logged-in users:

```typescript
function isLoggedIn(userIdentifier: UserIdentifier, loggedInUsers: readonly UserIdentifier[]): boolean {
    return loggedInUsers.includes(userIdentifier);
}
```

A set has better asymptotic lookup complexity, but a small array is stored contiguously and may be faster in practice.
Even if the set is faster, changing the code does not solve a real problem unless this operation has become a
user-visible bottleneck.

Use this order when optimizing:

1. Define the latency, throughput, memory, or power-consumption metric that needs improvement.
2. Measure a baseline using representative data and environments.
3. Use a profiler to identify the actual hotspot.
4. Prefer large algorithmic or data-structure improvements.
5. Measure again after the change.
6. Only if the target is still not met, consider allocation, caching, and instruction-level details.

Do not reject the extraction of a readable function because of an unverified function-call cost. Do not slow down code
review over tiny differences such as `++index` versus `index++`.

Known real-time deadlines, device memory limits, and service-level objectives are genuine requirements and should
influence the design early. The project stage also changes the tradeoff: early products usually benefit more from
delivery speed and adaptability, while performance may deserve greater weight near release when the workload is well
understood.

## A Shared Decision-Making Framework

These principles ultimately serve the same goal: making code easy to understand and change under real-world constraints.

- Names and types reduce the amount of context readers must infer.
- Guard clauses, function extraction, and data pipelines reduce the amount of state that must be held in mind at once.
- Composition and dependency injection keep changes within explicit boundaries.
- Restraint in abstraction and micro-optimization avoids adding present complexity for problems that have not yet
  occurred.

When principles conflict, do not search for a mechanical rule that covers every case. First understand why the current
code exists, how it is most likely to change, and which constraints have already been demonstrated. Then choose the
simplest design that remains clear.

When a counterintuitive implementation is necessary, preserve the concrete reason near the code so that the next reader
does not have to repeat the same investigation.

# Project Instructions

Before modifying project code, read and follow `CONTRIBUTING.md`.

The contribution guide contains normative code design, compatibility, runtime transformation, and verification
requirements.

Apply these rules only within the scope of the current task. Do not refactor unrelated code solely to make it conform.