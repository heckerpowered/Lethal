# Code design

Judge a change by what the next maintainer will have to infer, and which parts they will have to change together.

## Names, types, and control flow

Place declarations that readers look up or compare together next to each other. When several types support the
same operations, group short overloads by operation. Keep one-line declarations adjacent within each group and
leave a blank line where the operation changes.

Name the concept in the vocabulary its callers use. Keep familiar terms such as GPU and UUID. Spell out a local
abbreviation if a reader would have to look it up. Include a unit or reference point when the type leaves it open.
`offsetBytes` identifies a unit, while a parameter of type `Duration` already carries one. Preserve familiar names
from external APIs. Prefixes that just announce a Boolean, interface, or base class add little. A name such as
`Helper` is a cue to look for the component that actually owns the operation.

Prefer names and types that make a convention visible at the call site. For example, an optional result can express
absence wherever it is used. Handle cases that cannot proceed early so the main path reads in execution order.
Extract a complex branch under a name that states its job. Three nested levels may prompt a second look.
Tree traversal, a resource scope, or a transaction can justify each level. Preserve scope, cleanup, and rollback.

Keep calculations explicit about their inputs. Separate them from I/O, clocks, randomness, and persistence where
practical. Use a pipeline when its stages explain the transformation. Extract complex lambdas into named functions.
Keep a straightforward loop when recursion would obscure the flow or risk exhausting the stack.

## Ownership and abstraction

A little duplication is often cheaper than making independent features share a lifecycle. Before consolidating
code, identify the decision that callers should be able to leave to another component. A shared interface is useful
when a caller needs independence from a concrete implementation, even if there is only one implementation today.
Counting similar code blocks tells you little about whether they will change together.

Prefer composition for capabilities that vary independently. A subclass forced to implement meaningless methods to
reuse one operation is a reason to separate those capabilities. Weigh that benefit against the initialization,
forwarding, and duplication composition introduces. Inheritance fits framework extension points and stable
hierarchies, and migration needs a benefit worth its cost. In extensible classes, expose narrow hooks, keep fields
private where possible, and use `final` or `sealed` where the extension contract permits it.

Let the code that configures a component choose dependencies that vary with platform, lifetime, or test environment.
Passing a concrete collaborator can be sufficient. If a test needs to mutate private state or call a private method,
examine which responsibilities belong together before widening visibility.

## Readability under performance constraints

A suspected function-call cost is a poor reason to reject a clear extraction. Use representative workload evidence
when weighing readability against speed. Investigate allocation, caching, or instruction costs when measurements
justify that level of detail. Early in development, delivery and adaptability often carry more weight. Near release,
a well-understood workload, a service-level objective, or a device budget may justify a different tradeoff.
