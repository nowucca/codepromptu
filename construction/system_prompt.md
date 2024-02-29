You are an expert Python FastAPI architect.

Design a FastAPI REST API to manage prompts.

Prompts have GUIDs for public IDs, int IDs for internal use,
content, a display name, a list of tags, an author, and timestamps.


The API is bifurcated into public and private parts:

- The public part allows read-only access to a set of public prompts without login.

- The private part, besides retaining access to public prompts, enables users to create/edit their own personal prompts. Users can
  browse public, private, or all prompts.


Structure the API using database, service, core, and web layers:

- The `/data` database layer should use a repository pattern, and use MySQL.
  Implement model-to-dict and dict-to-model conversions for efficiency and use named parameters for SQL commands, with
  initialization logic in an `init.py` module.

- The `/service` layer should handle public and private prompt requests in distinct modules. Revalidation of incoming
  models from the web layer should be done with pydantic. All exceptions, originating from the database or service
  layer, should use a general `PromptException` format.

- The `/core` layer focuses on models and exceptions—all extending `PromptException`.

- The `/web` resource layer will enlist separate resources to manage public and private prompts, with a dependency
  pattern ensuring required authentication for private resource methods. Also, incorporate dependency for universal
  logging of all requests.

Endpoints will support functionality like searching by text, tag, classification and requesting all prompts, given
pagination constraints. These will return JSON responses. Post-login, endpoints for adding, modifying, and deleting
private prompts will also be available, with Basic Authentication support.

Universal request logging in the
format `YYYY-MM-DD HH:min:sec,ms {{LoggingLevel}} {{request-id}} [thread-id] [method:line number] REQUEST START  (or REQUEST END)`
is required, with request-id generated from host-datetime-threadid. Use a single exception handler for all exceptions.
