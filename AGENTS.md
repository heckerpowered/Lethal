# Working on Lethal

Start with the requested behavior, correctness, safety, and compatibility across supported hosts. Follow the
conventions of the affected module, language, and host API. Within those constraints, prefer the design the next
reader can most easily understand and change. Keep unrelated stylistic cleanup out of the patch.

If another part of the program gives a state change its meaning, extract a private operation even when it has
only one caller. Name it for the effect the caller requests, including when that effect takes place if timing
matters. Keep steps inline when their existing names already tell the reader what happens and when.

Use the guide that covers the decision at hand:

- When choosing names, restructuring control or data flow, or deciding whether to share an abstraction or pass in a
  dependency, use [code design](docs/contributing/code-design.md).
- If an API change leaves callers unable to predict its behavior from the declaration alone, add or update its KDoc
  as part of the change. For KDoc and comments explaining implementation choices, use the
  [documentation guide](docs/contributing/documentation.md).
- When changing a Mixin, injecting an interface, or exposing a host member through a transformation, follow the
  [Mixin rules](docs/contributing/mixins.md).
- When changing resource ownership, destruction, or cleanup failure handling, follow the
  [resource lifetime rules](docs/contributing/resource-lifetime.md). Project-owned destructors use `terminateOnFailure`.

For performance work, start with the metric and a representative baseline. Use an existing trace or profile the
workload to locate the cost. Prefer algorithm or data-structure changes that address it, then measure again.
Investigate lower-level costs when the target still calls for it. Known real-time deadlines and device memory limits
should inform the design from the outset.

When the requested deliverable includes a render, capture, or benchmark, produce and inspect it. For visual changes,
judge the moment and camera view the user specified. If the result misses the requested target, iterate on that check.
