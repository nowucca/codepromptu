## Core Engineering Design: CodePromptu Prompt Capture & Template Engine

### 1. Overview

This design captures the zero-touch prompt capture and template extraction pipeline at the heart of CodePromptu. It sits transparently between clients and LLM endpoints, detecting, classifying, and storing every prompt and its variables without any client modifications.

### 2. API Gateway & Proxy

**Role**: Acts as an API gateway that clients connect to via base URL configuration while maintaining their own API keys.

* **Client Integration**: Clients configure their LLM libraries to use CodePromptu's base URL (e.g., `https://api.codepromptu.com/v1/`) instead of direct provider URLs.
* **API Key Pass-through**: Clients continue using their own provider API keys; CodePromptu passes these through to the appropriate LLM provider.
* **Request/Response interception**:

  1. Receive client request with client's provider API key.
  2. Capture prompt payload for analysis.
  3. Forward to appropriate LLM provider using the client's API key.
  4. Capture and return the response seamlessly.

### 3. Prompt Embedding & Reuse Detection

1. **Mask variables**:

   * Regex-based replace of `{{...}}` patterns (or generic heuristic) with `__VAR__` tokens.
2. **Embed masked prompt** using embeddings model.
3. **Similarity lookup** in Vector DB:

   * `score ≥ SAME_THRESHOLD` (e.g., 0.95): existing prompt → record new usage.
   * `FORK_LOWER ≤ score < SAME_THRESHOLD` (e.g., 0.70–0.95): variant → fork with parent link.
   * `score < FORK_LOWER`: brand-new prompt.

### 4. Template Induction & Variable Extraction

**Clustering & Shell Discovery** (background job)

1. Periodically find clusters of raw prompts with similarity ≥ CLUSTER\_THRESHOLD (e.g., 0.8).
2. Compute Longest Common Subsequence across cluster to derive `shell` fragments.
3. Generate `__VARn__` placeholders between fragments; store in `prompt_templates`.

**Runtime Extraction**

1. Match incoming raw prompt to nearest `prompt_templates.shell`.
2. Align on `fragments` to extract each `__VARn__` value.
3. Persist usage with `template_id` and `variables JSONB`.

### 5. Data Models

```sql
-- Unique prompts and forks
CREATE TABLE prompts (
  id UUID PRIMARY KEY,
  parent_id UUID REFERENCES prompts(id),
  content TEXT NOT NULL,
  embedding VECTOR,
  metadata JSONB,
  created_at TIMESTAMPTZ
);

-- Template definitions
CREATE TABLE prompt_templates (
  id UUID PRIMARY KEY,
  shell TEXT,
  fragments TEXT[],
  embedding VECTOR,
  created_at TIMESTAMPTZ
);

-- Every captured call
CREATE TABLE prompt_usages (
  id UUID PRIMARY KEY,
  prompt_id UUID REFERENCES prompts(id),
  template_id UUID REFERENCES prompt_templates(id),
  variables JSONB,
  convo_id UUID,
  request_ts TIMESTAMPTZ,
  response_ts TIMESTAMPTZ,
  tokens_in INT,
  tokens_out INT,
  status TEXT,
  response TEXT
);
```

### 6. Conversation Grouping (Optional)

* On first request, proxy issues `Set-Cookie: codepromptu_convo_id=<uuid>`.
* Subsequent calls include `codepromptu_convo_id`; map to `usages.convo_id`.

### 7. Configuration & Tuning

* **Thresholds**: adjust SAME\_THRESHOLD, FORK\_LOWER, CLUSTER\_THRESHOLD.
* **Metadata**: capture client source, model params, IP, headers.
* **Dashboard**: UI to merge templates, reclassify forks, review usages.

---

This capture-and-template engine forms the backbone of CodePromptu’s ability to track prompt reuse, evolution, and variable bindings—all with zero client changes.
