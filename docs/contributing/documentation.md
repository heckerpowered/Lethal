# Comments and API documentation

Write for someone who knows the language and is meeting the concept for the first time. Start with the role it
plays for the caller. A reader should be able to predict the outcome of a realistic use or choose between valid
options. When two plausible interpretations lead to different outcomes, show one small example that settles the
question. One sentence can be enough for a simple operation.

When several inputs determine a result, explain how they combine in words or a formula a caller can apply to a
new case. Choose example values that reveal the relationship. If one channel sets the scale for all channels,
use unequal values and show how that scale affects another channel's output.

Explain the parts of a public contract a declaration leaves open. For a range, that might be the unit, origin, or
end boundary. For a borrowed value, it might be who owns the storage, which operation invalidates it, or which
thread may access it. Describe ordering, failures, recovery, or resource costs when they affect a caller's decision.
For validation, be precise about what success establishes and which checks still belong to the caller or backend.
Put each detail on the declaration where a reader needs it. Link related declarations using KDoc references.

Use a clearer name, type, or control structure when it can express the intent directly. An implementation comment
earns its place when it preserves reasoning a maintainer would otherwise have to reconstruct. That might be an
overflow avoided by a calculation, a measured tradeoff, a formula from a paper, or a workaround for a host defect.

## Examples from the vertex-input API

These examples illustrate different reasons to expand a KDoc.

- [VertexStepMode.Instance](../../render/render-hardware-interface/src/main/kotlin/heckerpowered/render/pipeline/vertex/VertexStepMode.kt)
  draws one cube mesh three times to show which values change per instance and which every vertex shares.
- [VertexBufferLayout.validateAccess](../../render/render-hardware-interface/src/main/kotlin/heckerpowered/render/pipeline/vertex/VertexBufferLayout.kt)
  makes a boundary concrete. Three elements at a stride of 32 bytes with one 12-byte attribute at offset zero
  require a 76-byte view because the final read covers bytes 64 through 75. The inline comment explains why the bounds
  check compares capacity.
- [VertexFormat](../../render/render-hardware-interface/src/main/kotlin/heckerpowered/render/pipeline/vertex/VertexFormat.kt)
  gives `Sint32x3` one sentence. The longer `Float32x4` entry helps a caller weigh color precision against bandwidth.
