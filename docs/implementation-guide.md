# Appendix D: CodePromptu Implementation Build Narrative

This appendix provides the complete technical implementation narrative for CodePromptu, demonstrating how the Constellize method translates from concept to working code. The examples here show the full journey from planning to deployment, illustrating how prompt-driven development creates systems that are both functional and well-documented.

---

## Planning Implementation

*Transforming vision into actionable milestones and modules.*

**Conversation:**

**User:** *"Outline an implementation plan: list modules, assign sprints, specify dependencies."*

**AI:**

| Sprint | Module     | Deliverable                        |
| ------ | ---------- | ---------------------------------- |
| 1      | proxy      | basic /capture endpoint + tests    |
| 2      | embeddings | `compute_similarity` + unit tests  |
| 3      | models     | SQLModel schema + migrations       |
| 4      | decisions  | decision capture + history queries |
| 5      | clusters   | /clusters endpoint + load test     |

**Reflection:** We adjust to match team capacity—splitting sprint 5 into two weeks: one for clustering logic, one for monitoring dashboards. This planning approach ensures each sprint delivers working functionality while building toward the complete system.

---

## Generating Modules & Tests

*Demonstrating Constellize's power to scaffold code and tests in unison.*

**Conversation:**

**User:** *"Create `models.py` with SQLModel classes for Prompt, Version, Decision; generate pytest files under `tests/`. Include fixtures for an in-memory SQLite database."*

**AI:**

```python
# tests/conftest.py
import pytest
from sqlmodel import SQLModel, create_engine, Session
from app.models import Prompt, Version, Decision

@pytest.fixture()
def session():
    engine = create_engine("sqlite://", echo=True)
    SQLModel.metadata.create_all(engine)
    with Session(engine) as s:
        yield s

@pytest.fixture()
def sample_prompt(session):
    prompt = Prompt(
        template_text="Generate a {{task_type}} for {{domain}}",
        variables=["task_type", "domain"],
        embedding_model="text-embedding-ada-002",
        vector_dim=1536
    )
    session.add(prompt)
    session.commit()
    session.refresh(prompt)
    return prompt

@pytest.fixture()
def sample_decision(session, sample_prompt):
    decision = Decision(
        prompt_id=sample_prompt.id,
        previous_version=None,
        new_version=1,
        similarity_score=0.95,
        action="store"
    )
    session.add(decision)
    session.commit()
    session.refresh(decision)
    return decision
```

```python
# tests/test_models.py
import pytest
from app.models import Prompt, Decision, MemoryEntry

def test_prompt_creation(session):
    prompt = Prompt(
        template_text="Test prompt with {{variable}}",
        variables=["variable"],
        embedding_model="text-embedding-ada-002",
        vector_dim=1536
    )
    session.add(prompt)
    session.commit()
    
    assert prompt.id is not None
    assert prompt.template_text == "Test prompt with {{variable}}"
    assert "variable" in prompt.variables

def test_decision_with_memory_entries(session, sample_prompt):
    decision = Decision(
        prompt_id=sample_prompt.id,
        previous_version=None,
        new_version=1,
        similarity_score=0.85,
        action="fork"
    )
    session.add(decision)
    session.commit()
    
    # Add memory entries
    memory_entries = [
        MemoryEntry(decision_id=decision.id, key="user_role", value="engineer"),
        MemoryEntry(decision_id=decision.id, key="model_version", value="gpt-4"),
        MemoryEntry(decision_id=decision.id, key="repo_branch", value="feature/new-prompts")
    ]
    
    for entry in memory_entries:
        session.add(entry)
    session.commit()
    
    # Verify the decision and its context
    retrieved_decision = session.get(Decision, decision.id)
    assert retrieved_decision.action == "fork"
    assert retrieved_decision.similarity_score == 0.85
```

**Reflection:** We run `pytest -q`, observe green dots, and integrate CI pipeline to run tests on each PR. This approach ensures that both models and tests evolve together, maintaining system reliability as features are added.

---

## PoC Implementation Build Narrative

*Here we choose minimal libraries and a single Docker container for a working Proof-of-Concept.*

**Conversation:**

**User:** *"Generate a FastAPI app using SQLModel and SQLite, including `Prompt`, `Version`, `Decision` models; a `/capture` endpoint; and startup logic to create `codepromptu.db`. Also provide Dockerfile for a single container."*

**AI:**

```python
# server/main.py
from fastapi import FastAPI, HTTPException
from sqlmodel import SQLModel, create_engine, Session, select
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Dict, Any
import asyncio

from app.models import Prompt, Version, Decision, MemoryEntry
from app.embeddings import compute_similarity_async

# Database setup
DATABASE_URL = "sqlite:///./codepromptu.db"
engine = create_engine(DATABASE_URL, echo=True)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    SQLModel.metadata.create_all(engine)
    yield
    # Shutdown
    pass

app = FastAPI(
    title="CodePromptu API",
    description="Capture, evaluate, and evolve LLM prompts for software teams",
    version="0.1.0",
    lifespan=lifespan
)

@app.post("/capture")
async def capture_prompt(payload: Dict[str, Any]):
    """
    Capture a prompt and decide whether to store, fork, or reference based on similarity.
    """
    try:
        text = payload["prompt"]
        prompt_id = payload.get("prompt_id")
        
        # Compute similarity if we have an existing prompt to compare against
        similarity_score = 0.0
        if prompt_id:
            with Session(engine) as session:
                existing_prompt = session.get(Prompt, prompt_id)
                if existing_prompt:
                    similarity_score = await compute_similarity_async(
                        text, existing_prompt.template_text
                    )
        
        # Decide action based on similarity threshold
        action = "store" if similarity_score < 0.7 else "fork" if similarity_score < 0.9 else "reference"
        
        # Create decision record
        with Session(engine) as session:
            decision = Decision(
                prompt_id=prompt_id or 0,
                previous_version=payload.get("previous_version"),
                new_version=payload.get("new_version", 1),
                similarity_score=similarity_score,
                action=action,
                timestamp=datetime.utcnow()
            )
            session.add(decision)
            session.commit()
            session.refresh(decision)
            
            # Store context in memory entries
            context_fields = {
                "user_role": payload.get("user_role", "engineer"),
                "model_version": payload.get("model_version", "gpt-4"),
                "repo_branch": payload.get("repo_branch", "main"),
                "purpose": payload.get("purpose", "general")
            }
            
            for key, value in context_fields.items():
                if value:  # Only store non-empty values
                    memory_entry = MemoryEntry(
                        decision_id=decision.id,
                        key=key,
                        value=str(value),
                        timestamp=datetime.utcnow()
                    )
                    session.add(memory_entry)
            
            session.commit()
            
            return {
                "decision_id": decision.id,
                "action": action,
                "similarity_score": similarity_score,
                "prompt_id": prompt_id
            }
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error processing prompt: {str(e)}")

@app.get("/memory")
async def get_memory(decision_id: int):
    """
    Retrieve memory context for a specific decision.
    """
    with Session(engine) as session:
        memory_entries = session.exec(
            select(MemoryEntry).where(MemoryEntry.decision_id == decision_id)
        ).all()
        
        if not memory_entries:
            raise HTTPException(status_code=404, detail="No memory found for this decision")
        
        return [
            {
                "key": entry.key,
                "value": entry.value,
                "timestamp": entry.timestamp.isoformat()
            }
            for entry in memory_entries
        ]

@app.get("/prompts/{prompt_id}/history")
async def get_prompt_history(prompt_id: int):
    """
    Get the decision history for a specific prompt.
    """
    with Session(engine) as session:
        decisions = session.exec(
            select(Decision).where(Decision.prompt_id == prompt_id)
        ).all()
        
        return [
            {
                "decision_id": decision.id,
                "action": decision.action,
                "similarity_score": decision.similarity_score,
                "version": decision.new_version,
                "timestamp": decision.timestamp.isoformat()
            }
            for decision in decisions
        ]

@app.get("/health")
async def health_check():
    """Simple health check endpoint."""
    return {"status": "healthy", "timestamp": datetime.utcnow().isoformat()}
```

```python
# app/models.py
from sqlmodel import SQLModel, Field
from datetime import datetime
from typing import List, Optional

class Prompt(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    template_text: str
    variables: List[str] = Field(default_factory=list)
    embedding_model: str = Field(default="text-embedding-ada-002")
    vector_dim: int = Field(default=1536)
    created_at: datetime = Field(default_factory=datetime.utcnow)

class Version(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    prompt_id: int = Field(foreign_key="prompt.id")
    version_number: int
    template_text: str
    created_at: datetime = Field(default_factory=datetime.utcnow)

class Decision(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    prompt_id: int = Field(foreign_key="prompt.id")
    previous_version: Optional[int]
    new_version: int
    similarity_score: float
    action: str  # "store", "fork", "reference"
    timestamp: datetime = Field(default_factory=datetime.utcnow)

class MemoryEntry(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    decision_id: int = Field(foreign_key="decision.id")
    key: str
    value: str
    timestamp: datetime = Field(default_factory=datetime.utcnow)
```

```python
# app/embeddings.py
import re
import asyncio
from typing import List
import numpy as np

async def compute_similarity_async(text_a: str, text_b: str) -> float:
    """
    Compute similarity between two prompts by masking variables and comparing.
    For PoC, we use a simple text-based similarity. In production, this would
    call OpenAI's embeddings API.
    """
    # Mask variable placeholders
    mask_pattern = re.compile(r"\{\{\w+\}\}")
    a_masked = mask_pattern.sub("__VAR__", text_a.lower())
    b_masked = mask_pattern.sub("__VAR__", text_b.lower())
    
    # Simple similarity based on common words (for PoC)
    words_a = set(a_masked.split())
    words_b = set(b_masked.split())
    
    if not words_a and not words_b:
        return 1.0
    if not words_a or not words_b:
        return 0.0
    
    intersection = len(words_a.intersection(words_b))
    union = len(words_a.union(words_b))
    
    return intersection / union if union > 0 else 0.0

# For production use with OpenAI:
"""
from openai import AsyncOpenAI

async def compute_similarity_async(text_a: str, text_b: str) -> float:
    mask = re.compile(r"\\{\\{\\w+\\}\\}")
    a_masked = mask.sub("__VAR__", text_a)
    b_masked = mask.sub("__VAR__", text_b)
    
    client = AsyncOpenAI()
    response = await client.embeddings.create(
        input=[a_masked, b_masked],
        model="text-embedding-ada-002"
    )
    
    embeddings = [data.embedding for data in response.data]
    
    # Cosine similarity
    dot_product = np.dot(embeddings[0], embeddings[1])
    norm_a = np.linalg.norm(embeddings[0])
    norm_b = np.linalg.norm(embeddings[1])
    
    return dot_product / (norm_a * norm_b)
"""
```

```dockerfile
# Dockerfile
FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY server/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY server/ .

# Create directory for database
RUN mkdir -p /app/data

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# Run the application
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]
```

```txt
# server/requirements.txt
fastapi==0.104.1
uvicorn[standard]==0.24.0
sqlmodel==0.0.14
sqlite3
pydantic==2.5.0
python-multipart==0.0.6
```

**User:** *"Provide commands to build and run the container without Docker Compose."*

**AI:**

```bash
# Build the container
docker build -t codepromptu-poc .

# Run the container with volume mount for database persistence
docker run --rm -p 8000:8000 \
  -v "$PWD/data:/app/data" \
  --name codepromptu \
  codepromptu-poc

# Alternative: Run in background
docker run -d -p 8000:8000 \
  -v "$PWD/data:/app/data" \
  --name codepromptu \
  codepromptu-poc

# Test the API
curl -X POST "http://localhost:8000/capture" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Generate a {{task_type}} for {{domain}}",
    "user_role": "engineer",
    "model_version": "gpt-4",
    "repo_branch": "main",
    "purpose": "code generation"
  }'

# Check memory for a decision
curl "http://localhost:8000/memory?decision_id=1"

# View API documentation
open http://localhost:8000/docs
```

**Reflection:** We test via `curl`, mock embeddings in tests, and confirm a working PoC in one container. This chapter closes the loop: from prompt-driven design to a live service you can deploy anywhere Docker runs.

The complete implementation demonstrates several Constellize principles in action:

1. **Memory as Infrastructure**: The MemoryEntry model ensures context travels with decisions
2. **Evolutionary Design**: The similarity scoring enables intelligent prompt forking and evolution  
3. **Contextual Generation**: Each component was shaped by the specific needs identified during knowledge gathering
4. **Operational Readiness**: Health checks, proper error handling, and clear API documentation support production deployment

This PoC provides a foundation that teams can extend with additional features like clustering, advanced embeddings, or integration with existing development tools - all while maintaining the contextual memory that makes the system intelligible and adaptable over time.
