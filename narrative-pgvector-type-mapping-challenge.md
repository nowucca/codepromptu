# PGvector Type Mapping Challenge - Technical Deep Dive

## Problem Statement
We've encountered a persistent issue with Hibernate's handling of the PGvector type in our CodePromptu API. Despite multiple approaches, Hibernate continues to serialize PGvector objects as `bytea` instead of the proper PostgreSQL `vector` type, resulting in the error:

```
ERROR: column "embedding" is of type vector but expression is of type bytea
```

## Approaches Attempted

### 1. Custom UserType Implementation
- Created `PGvectorType` implementing Hibernate's `UserType<PGvector>` interface
- Used `@Type(PGvectorType.class)` annotation on the entity field
- Result: Hibernate ignored the custom type, still serialized as bytea

### 2. TypeContributor Registration
- Created `HibernateConfig` implementing `TypeContributor`
- Registered via META-INF services file
- Result: Type contributor was loaded but still didn't affect serialization

### 3. JdbcTypeCode Annotation
- Used `@JdbcTypeCode(SqlTypes.OTHER)` annotation
- Result: No change in behavior

### 4. AttributeConverter Approach
- Created `PGvectorConverter` implementing `AttributeConverter<PGvector, String>`
- Used `@Convert(converter = PGvectorConverter.class)` annotation
- Result: Still the same bytea serialization issue

## Root Cause Analysis
The issue appears to be that:
1. The PostgreSQL JDBC driver is handling PGvector objects correctly
2. Hibernate's ORM layer is intercepting and overriding the type handling
3. Despite our custom type mappings, Hibernate defaults to bytea serialization for complex objects

## Current Status
- API service is running and healthy
- All endpoints except prompt creation are working
- Vector embedding generation is working (EmbeddingService functions correctly)
- Database schema is correct (vector column exists with proper type)
- The issue is specifically in the Hibernate → PostgreSQL type mapping layer

## Next Steps
We need to implement a workaround that either:
1. Bypasses Hibernate's type system entirely for this field
2. Uses a different approach to store vector embeddings
3. Implements a custom solution that works with the current Hibernate version

## Technical Context
- Spring Boot 3.2.2
- Hibernate 6.x (via Spring Boot)
- PostgreSQL with pgvector extension
- pgvector-java library for PGvector objects

This represents a complex interaction between multiple layers of the stack, and may require a more fundamental architectural decision about how we handle vector embeddings in the system.
