# 🛠️ CodePromptu: Constellize Build Plan

This document describes how CodePromptu is constructed using the **Constellize method**, including use of a memory bank, construction zone, and knowledge stewardship practices. These are internal engineering practices — not exposed features — but they shape how the system evolves and stays resilient over time.

---

## 📂 1. Construction Zone (`/construction/`)

The construction zone is a dedicated directory for exploratory development and implementation planning.

### Contents:

* `implementation_plan.md` — Numbered subtasks and milestones
* Prototypes, schema sketches, and early UI mockups
* Design alternatives and trade-off records
* Pre-PR experiments and forks

### Purpose:

* Encourage fast iteration without polluting stable code
* Capture rationale before implementation
* Align team before finalizing structure

---

## 🧠 2. Memory Bank (`/memory-bank/`)

A structured directory of markdown documents that captures evolving system knowledge.

### Suggested Files:

* `projectbrief.md` — High-level problem and goals
* `productContext.md` — Target users, use cases, constraints
* `systemPatterns.md` — Architecture notes, known trade-offs
* `techContext.md` — Stack, services, APIs, integration notes
* `activeContext.md` — Current development focus and open questions
* `progress.md` — Completed steps, known bugs, rationale

### Update Triggers:

* After feature delivery or pivot
* Postmortems or incident resolution
* Architectural or dependency changes
* After prompt or system evaluation cycles

### Agent/LLM Integration:

The memory bank may be loaded by agents (e.g., chat assistants, VS Code plugins) to provide continuity and reasoning assistance.

---

## 🔁 3. Stewardship Practices

* Assign owners to each constellation (e.g., storage, evaluation, agent-integration)
* Quarterly review of memory bank for outdated knowledge
* Fork or annotate legacy knowledge with rationale
* Link changes to relevant construction plans and operational runbooks

---

## 🔗 4. Federation and Cross-Team Links

* Use `crossref:` tags to link knowledge between memory banks (e.g., `crossref:team-mlinfra`)
* Connect to constellations from related systems (e.g., agents, analytics, plugins)
* Maintain lightweight maps of upstream/downstream relationships

---

## 🧪 5. Operational Extension

The memory bank is extended to include operational resilience data:

* `runbooks/` for prompt-serving and vector DB issues
* `incident-reports/` with context, timelines, and lessons
* Cross-references to design assumptions and constraints

These entries are treated as first-class constellation components for on-call and SRE use.

---

## 📎 Referenced From

This document is referenced in the main **Product Requirements Document (PRD)** for CodePromptu under the heading *Development Process*.
