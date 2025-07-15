### Lean RACI Matrix

| #  | Module / Phase                          | Prompt Engrs | ML Infra  | Agent Devs | SRE / Ops | Product Owner / PM | Eng. Tooling |
| -- | --------------------------------------- | ------------ | --------- | ---------- | --------- | ------------------ | ------------ |
| 1  | **Data-schema design**                  |              | **A**     |            |           | **I**              | **R**        |
| 2  | **Prompt-storage & versioning service** | **R**        |           |            | **I**     |                    | **A**        |
| 3  | **Vector-search integration**           |              | **A / R** | **I**      |           |                    |              |
| 4  | **Forking & lineage logic**             | **R**        |           | **I**      |           |                    | **A**        |
| 5  | **Prompt-evaluation pipelines**         | **I**        | **A / R** |            |           |                    |              |
| 6  | **Evaluation dashboards**               | **R**        | **C**     | **I**      | **C**     | **A**              |              |
| 7  | **IDE / Slack plugins**                 | **I**        |           |            |           | **I**              | **A / R**    |
| 8  | **Operational runbooks & monitoring**   | **I**        |           |            | **A / R** | **I**              |              |
| 9  | **Cross-ref metadata & team ownership** | **A**        | **C**     | **C**      |           | **I**              | **R**        |
| 10 | **Deployment & rollout**                | **I**        | **C**     | **I**      | **A / R** | **I**              |              |

**Legend**

* **R** = Responsible • **A** = Accountable • **C** = Consulted • **I** = Informed

This keeps communication channels lean while ensuring at least one stakeholder is **Informed** for every module.
