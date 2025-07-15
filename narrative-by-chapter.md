# CodePromptu Narrative: A Constellize Journey

> *Note: Some chapters (e.g., Requirements and Engineering Documentation) appear out of linear order; see the appendix for a link to the GitHub repository and a comprehensive list of all prompts.*

This narrative demonstrates the Constellize method in action, weaving our back-and-forth prompts and AI responses into a cohesive story. Each section includes the actual conversation snippets, expanded detail on decision points, and reflections on how generated insights shape our evolving code.

---

## Chapter 2: Framing the Vision

*Spotlight how a high-level prompt sets our direction before code emerges.*

**Conversation:**

**User:** *"Write a vision statement for CodePromptu: a service that captures, evaluates, and evolves LLM prompts for software teams."*

**AI:** *"CodePromptu empowers engineering teams to treat prompts as living artifacts—capturing context, measuring effectiveness, and evolving templates so that decisions drive quality, not guesswork."*

**Reflection:**  We pin that vision at the top of `projectbrief.md`. Whenever scope creeps or features conflict, returning to this statement realigns stakeholders. It also becomes the source of truth for our README introduction and marketing blurb.

We spend time discussing tone and emphasis:

**User:** *"Emphasize that context—not just code—drives robustness. Add a metaphor about prompts as blueprints."*

**AI:** *"Think of CodePromptu as a digital architect: every prompt is a blueprint stored, tested, and refined until it withstands the weight of evolving requirements."*

We tweak and finalize, creating a multi-part vision section that lives in both `docs/vision.md` and the book’s introduction.

---

## Chapter 3: Gathering Domain Knowledge

*Illustrate how prompting extracts schema and context to prevent misinterpretation.*

**Conversation:**

**User:** *"List core metadata fields for every captured prompt in CodePromptu, including variable placeholders, embedding dimensions, and context tags."*

**AI:**

> * `template_text`: the masked prompt with variables replaced by placeholders
> * `variables`: list of placeholder names and types
> * `embedding_model`: model name (e.g., `text-embedding-ada-002`)
> * `vector_dim`: embedding dimension (e.g., 1536)
> * `user_role`: role invoking the prompt (engineer, CI pipeline, etc.)
> * `timestamp`: ISO 8601 timestamp of invocation
> * `similarity_thresholds`: fork vs. store breakpoints
> * `action_history`: list of past versioning actions

**Reflection:**  We convert this into an ER diagram using FigJam, drafting tables:

* **prompts** (`id`, `template_text`, `variables`, `vector_dim`, `embedding_model`)
* **versions** (`id`, `prompt_id`, `version_number`)
* **decisions** (`id`, `version_id`, `timestamp`, `similarity_score`, `action`)
* **usages** (`id`, `version_id`, `user_role`, `timestamp`)

Capturing knowledge before coding prevents schema drifts and aligns the database design with real usage scenarios.

---

## Chapter 4: Filling the Gaps

*Show how Constellize reuses proven patterns and generates only missing pieces.*

**Conversation:**

**User:** *"Generate a Python function `compute_similarity(prompt_a, prompt_b)` that masks variable parts, calls the OpenAI embeddings API, and returns a float similarity score."*

**AI:**

```python
import re
from openai import OpenAI

async def compute_similarity(text_a: str, text_b: str) -> float:
    mask = re.compile(r"\{\{\w+\}\}")
    a_masked = mask.sub("__VAR__", text_a)
    b_masked = mask.sub("__VAR__", text_b)
    client = OpenAI()
    resp = await client.embeddings.create(
        input=[a_masked, b_masked],
        model="text-embedding-ada-002"
    )
    vecs = [d.embedding for d in resp.data]
    # cosine similarity
    dot = sum(x*y for x,y in zip(vecs[0], vecs[1]))
    norm = lambda v: sum(x*x for x in v)**0.5
    return dot / (norm(vecs[0]) * norm(vecs[1]))
```

**Reflection:**  We paste this into `app/embeddings.py`, rename functions for consistency (`compute_similarity_async`), and add type hints. By reusing this generated snippet, we eliminate hours of boilerplate.

---

## Chapter 5: Capturing Decisions & Adaptation

*Demonstrate capturing design decisions as we evolve our templates and thresholds — and storing full context in a memory bank.*

**Conversation:**

**User:** *"When a prompt arrives, record only the decision metadata: prompt\_id, previous\_version, new\_version, similarity\_score, action, timestamp. Also capture additional context fields (user\_role, model\_version, repo\_branch) so we can recall them later from the memory bank."*

**AI:** *"Understood. I’ll extend the Decision model and introduce a MemoryEntry model to store context payloads."*

```python
# app/models.py
class Decision(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    prompt_id: int = Field(foreign_key="prompt.id")
    previous_version: int | None
    new_version: int
    similarity_score: float
    action: str
    timestamp: datetime = Field(default_factory=datetime.utcnow)

class MemoryEntry(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    decision_id: int = Field(foreign_key="decision.id")
    key: str
    value: str
    timestamp: datetime = Field(default_factory=datetime.utcnow)
```

**AI (continued):** *"Now, in our `/capture` handler, we’ll insert both a Decision and its related MemoryEntries."*

```python
@app.post("/capture")
async def capture(payload: dict):
    # extract core
    text = payload["prompt"]
    sim = await compute_similarity_async(text, text)
    action = "store" if sim < 0.7 else "fork"
    # persist decision
    with Session(engine) as session:
        decision = Decision(
            prompt_id=payload.get("prompt_id", 0),
            previous_version=payload.get("previous_version"),
            new_version=payload.get("new_version", 1),
            similarity_score=sim,
            action=action
        )
        session.add(decision)
        session.commit()
        # persist memory entries
        context_fields = {
            "user_role": payload.get("user_role", "engineer"),
            "model_version": payload.get("model_version", "gpt-4"),
            "repo_branch": payload.get("repo_branch", "main")
        }
        for k, v in context_fields.items():
            entry = MemoryEntry(decision_id=decision.id, key=k, value=v)
            session.add(entry)
        session.commit()
    return {"decision_id": decision.id, "action": action, "similarity_score": sim}
```

**Reflection:**  Each Decision now links to multiple MemoryEntry rows, preserving the full context of that decision: who ran it, under which model, and on which branch. We can query `/memory?decision_id=123` to reconstruct context later.

**Demonstration:**

* **Prompt:** *GET `/memory?decision_id=42`*
* **Response:**

  ```json
  [
    {"key": "user_role", "value": "team_lead", "timestamp": "2025-07-14T10:25:00Z"},
    {"key": "model_version", "value": "gpt-4", "timestamp": "2025-07-14T10:25:00Z"},
    {"key": "repo_branch", "value": "feature/context-bank", "timestamp": "2025-07-14T10:25:00Z"}
  ]
  ```

By mixing decisions with context entries, our memory bank becomes a first-class citizen—each code evolution step carries its surrounding metadata, unlocking powerful post-hoc analysis, audit trails, and AI-driven insights.

---

## Chapters 6/7: Scaling and Operations

*Describe adding cross-references and scaling storage without losing context.*

**Conversation:**

**User:** *"Generate code to cluster prompts with similarity > 0.9 and expose GET /clusters endpoint returning clusters as JSON."*

**AI:**

```python
@app.get("/clusters")
async def clusters():
    prompts = session.exec(select(Prompt)).all()
    clusters = []
    for p in prompts:
        sim = compute_similarity(p.template_text, other_text)
        if sim > 0.9:
            # add to cluster logic
            pass
    return {"clusters": clusters}
```

We refine and implement hierarchical clustering with scipy, finalize endpoint, and load-test with hey to ensure <50ms responses.

---

## Chapter 8: Drafting Requirements

*Use prompting to bootstrap specifications, even if we iterate later.*

**Conversation:**

**User:** *"Produce a detailed requirements document for CodePromptu: include functional flows, non-functional SLAs, and user personas."*

**AI:**

> **Functional Requirements**
>
> 1. Capture raw prompts via POST /capture
> 2. Compute similarity and decide action: store, fork, or reference
> 3. Store decisions and prompt versions in database
> 4. Retrieve prompt history via GET /prompts/{id}/history
>
> **Non-Functional SLAs**
>
> * API latency < 200ms for capture
> * 99.9% uptime over 30 days
> * Data retention for 1 year
>
> **User Personas**
>
> * Engineer: uses CodePromptu to standardize prompts
> * Team Lead: reviews prompt evolution reports
> * Auditor: exports decision logs for compliance

We copy into `/docs/requirements.md`, add diagrams for flows, and annotate edge cases (e.g., retry logic).

---

## Chapter 9: Crafting Engineering Documentation

*Leverage AI to generate API contracts and architecture diagrams.*

**Conversation:**

**User:** *"Generate an OpenAPI spec for POST /capture and GET /prompts; include example requests and responses; then output PlantUML for the system overview."*

**AI:**

```yaml
openapi: 3.0.0
info:
  title: CodePromptu API
  version: 0.1.0
paths:
  /capture:
    post:
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                prompt:
                  type: string
      responses:
        '200':
          description: Decision recorded
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Decision'
components:
  schemas:
    Decision:
      type: object
      properties:
        prompt_id:
          type: integer
        action:
          type: string
```

<details>
<summary>PlantUML</summary>
```plantuml
title CodePromptu Architecture
envelope -> service: POST /capture
envelope <- service: 200 OK
service -> db: SQLModel commit
```  
</details>

We refine spec, validate with swagger-ui, and ensure PlantUML renders correctly in our docs pipeline.

---

## Chapter 10: Planning Implementation

*Show how to transform vision into milestones and modules.*

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

We adjust to match team capacity—splitting sprint 5 into two weeks: one for clustering logic, one for monitoring dashboards.

---

## Chapter 11: Generating Modules & Tests

*Demonstrate Constellize’s power to scaffold code and tests in unison.*

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
```

...

We run `pytest -q`, observe green dots, and integrate CI pipeline to run tests on each PR.

---

## Chapter 12: PoC Implementation Build Narrative

*Here we choose minimal libraries and a single Docker container for a working Proof-of-Concept.*

**Conversation:**

**User:** *"Generate a FastAPI app using SQLModel and SQLite, including `Prompt`, `Version`, `Decision` models; a `/capture` endpoint; and startup logic to create `codepromptu.db`. Also provide Dockerfile for a single container."*

**AI:**

```dockerfile
# Dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY server/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server .
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

```python
# server/main.py
from fastapi import FastAPI
from sqlmodel import SQLModel, create_engine
from app.models import Prompt, Version, Decision
from app.embeddings import compute_similarity_async

app = FastAPI()
engine = create_engine("sqlite:///./codepromptu.db")

@app.on_event("startup")
def init_db():
    SQLModel.metadata.create_all(engine)

@app.post("/capture")
async def capture(payload: dict):
    text = payload["prompt"]
    sim = await compute_similarity_async(text, text)
    action = "store" if sim < 0.7 else "fork"
    # persist logic...
    return {"prompt_id": 1, "action": action}
```

**User:** *"Provide commands to build and run the container without Docker Compose."*

**AI:**

```bash
docker build -t codepromptu-poc .
docker run --rm -p 8000:8000 -v "$PWD/codepromptu.db":/app/codepromptu.db codepromptu-poc
```

**Reflection:**  We test via `curl`, mock embeddings in tests, and confirm a working PoC in one container. This chapter closes the loop: from prompt-driven design to a live service you can deploy anywhere Docker runs.

---

This narrative shows Constellize as a living process—our evolving conversation fuels each code commit, test, and design artifact, illustrating that software emerges not from guesswork, but from guided, contextualized prompts and decisions.
