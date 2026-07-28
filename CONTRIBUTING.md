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