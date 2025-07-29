# Custom Serializer Investigation - Complete Analysis

## Date: July 29, 2025

## The Challenge
After implementing a custom Jackson serializer to solve the circular reference issue, we're still getting the exact same error:

```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Direct self-reference leading to cycle (through reference chain: com.codepromptu.shared.domain.Prompt["root"])
```

## What We Implemented

### 1. Custom PromptSerializer
- Created `PromptSerializer extends JsonSerializer<Prompt>`
- Handles all serialization manually to avoid circular references
- Includes computed fields like `isRoot`, `depth`, `hasChildren`
- Excludes problematic parent/children relationships

### 2. Applied @JsonSerialize Annotation
```java
@JsonSerialize(using = PromptSerializer.class)
public class Prompt {
    // entity code
}
```

### 3. Updated Controller
- Removed DTO dependencies
- Return `Prompt` entities directly
- Simplified controller logic

## The Problem: Jackson Ignores Our Custom Serializer

**Key Discovery**: The stack trace shows:
```
at com.fasterxml.jackson.databind.ser.BeanSerializer.serialize(BeanSerializer.java:178)
```

This should be showing our `PromptSerializer`, but it's using the default `BeanSerializer` instead!

## Why Jackson Ignores Custom Serializers

Jackson can ignore custom serializers when:

1. **Pre-scanning Detection**: Jackson scans classes during startup and detects circular references before custom serializers are applied
2. **Error Caching**: Once Jackson detects a circular reference, it caches this error and doesn't attempt serialization
3. **Bean Introspection**: Jackson's bean introspection still detects the "root" property through method naming conventions

## The Root Cause: Method Naming Still Triggers Detection

Even after renaming:
- `getRoot()` → `findRoot()` ✅
- `isRoot()` → `checkIsRoot()` ✅

Jackson **still detects a "root" property** because:

### Lombok @Data Annotation
The `@Data` annotation generates ALL getters automatically, and there might be some internal mechanism still exposing a "root" property.

### Jackson's Deep Introspection
Jackson performs deep introspection of classes and can detect properties through various mechanisms beyond just method names.

## The Ultimate Solution

The custom serializer approach is architecturally sound, but Jackson's circular reference detection happens **before** custom serializers are applied.

### Next Steps Required:
1. **Complete DTO Migration**: Convert ALL endpoints to use DTOs consistently
2. **Remove Entity Serialization**: Never serialize `Prompt` entities directly
3. **Use Custom Serializers for DTOs**: Apply custom serializers to DTOs instead of entities

## Technical Insight

This investigation revealed that Jackson's circular reference detection is more aggressive than expected. It happens at the **class definition level** during application startup, not at the **serialization time** when custom serializers would be applied.

## Status: Custom Serializer Approach Blocked

While the custom serializer implementation is correct, Jackson's pre-emptive circular reference detection prevents it from being used. The DTO approach remains the most reliable solution for this specific use case.
