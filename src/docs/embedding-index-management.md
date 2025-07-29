# Embedding Index Management

This document explains how CodePromptu automatically manages embedding indexes for optimal similarity search performance.

## Overview

CodePromptu uses PostgreSQL with the pgvector extension to store and search 1536-dimensional embeddings. The system automatically manages database indexes based on data volume to ensure optimal performance while avoiding PostgreSQL limitations.

## The Problem

PostgreSQL btree indexes cannot handle large vector dimensions due to size constraints:
- **Error**: `index row size 6160 exceeds btree version 4 maximum 2704`
- **Impact**: 1536-dimensional embeddings cannot be stored when a btree index exists on the embedding column
- **Solution**: Use pgvector-specific indexes (ivfflat) that are optimized for vector similarity search

## Automatic Index Management

### Database Migration (`02-fix-embedding-index.sql`)

The migration automatically:
1. **Drops problematic btree index**: Removes `idx_prompts_embedding` that prevents embedding storage
2. **Creates management functions**: Provides `create_embedding_index_if_needed()` and `rebuild_embedding_index()`
3. **Implements conditional logic**: Only creates vector indexes when sufficient data is available (≥100 prompts)

### Spring Boot Service (`EmbeddingIndexService`)

The service provides:
- **Startup check**: Evaluates index needs when application starts
- **Scheduled monitoring**: Hourly checks for index optimization opportunities
- **Dynamic management**: Creates, rebuilds, or drops indexes based on data volume
- **Health monitoring**: Exposes index status via health endpoints

## Index Lifecycle

### Phase 1: Development/Testing (< 100 prompts)
- **Status**: No vector index
- **Reason**: ivfflat indexes perform poorly with little data
- **Performance**: Acceptable for small datasets using sequential scans

### Phase 2: Production (≥ 100 prompts)
- **Status**: ivfflat index created automatically
- **Configuration**: `lists = max(prompt_count / 1000, 10)`
- **Performance**: Optimized for similarity search operations

### Phase 3: Scale (≥ 1000+ prompts)
- **Status**: Index automatically rebuilt with optimal list count
- **Monitoring**: Continuous optimization based on data growth
- **Performance**: Maintains optimal search performance at scale

## Deployment Integration

### Automatic Deployment
```bash
# Database migrations run automatically on container startup
# No manual intervention required

# Check index status after deployment
curl http://localhost:8081/health/embedding-index
```

### Manual Management (if needed)
```sql
-- Force index creation (if sufficient data exists)
SELECT create_embedding_index_if_needed();

-- Rebuild index with current data volume
SELECT rebuild_embedding_index();

-- Check current status
SELECT COUNT(*) FROM prompts WHERE embedding IS NOT NULL;
```

## Monitoring and Health Checks

### Health Endpoint
```bash
GET /health/embedding-index
```

**Response Example:**
```json
{
  "status": "UP",
  "timestamp": "2025-07-29T12:17:00",
  "stats": {
    "promptsWithEmbeddings": 1,
    "indexExists": true,
    "indexLists": 10,
    "minPromptsForIndex": 100,
    "shouldHaveIndex": false,
    "isOptimal": false
  },
  "recommendation": "Index should be removed - insufficient data"
}
```

### Status Meanings
- **UP**: Index configuration is optimal for current data volume
- **DEGRADED**: Index exists but may not be optimal (too few/many lists)
- **DOWN**: Error occurred while checking index status

## Configuration

### Environment Variables
```yaml
# No additional configuration required
# Service uses intelligent defaults based on data volume
```

### Database Functions
- `create_embedding_index_if_needed()`: Creates index if ≥100 prompts with embeddings
- `rebuild_embedding_index()`: Rebuilds index with optimal configuration

## Troubleshooting

### Common Issues

1. **Embeddings not storing**
   - **Cause**: Old btree index still exists
   - **Solution**: Run migration `02-fix-embedding-index.sql`

2. **Slow similarity search**
   - **Cause**: No vector index with sufficient data
   - **Solution**: Check `/health/embedding-index` and manually trigger if needed

3. **Index creation warnings**
   - **Warning**: "ivfflat index created with little data"
   - **Solution**: Normal for development; index will be optimized as data grows

### Manual Recovery
```sql
-- Emergency index removal (if blocking embeddings)
DROP INDEX IF EXISTS idx_prompts_embedding;
DROP INDEX IF EXISTS idx_prompts_embedding_ivfflat;

-- Recreate with proper configuration
SELECT create_embedding_index_if_needed();
```

## Performance Characteristics

### Without Index (< 100 prompts)
- **Storage**: ✅ Fast embedding storage
- **Search**: ⚠️ Sequential scan (acceptable for small datasets)
- **Memory**: ✅ Low memory usage

### With ivfflat Index (≥ 100 prompts)
- **Storage**: ✅ Fast embedding storage
- **Search**: ✅ Optimized vector similarity search
- **Memory**: ⚠️ Higher memory usage (scales with data)

## Best Practices

1. **Monitor index health**: Use `/health/embedding-index` in monitoring systems
2. **Allow automatic management**: Trust the service to optimize indexes
3. **Plan for scale**: Index performance improves with more data
4. **Test deployments**: Verify embedding storage works after deployment

## Future Enhancements

- **HNSW index support**: When pgvector adds better HNSW support
- **Custom thresholds**: Configurable prompt count thresholds
- **Advanced monitoring**: Detailed performance metrics
- **Multi-tenant optimization**: Per-tenant index strategies
