The task at hand is to design a REST API using Python's FastAPI framework. The API will be used to manage 'prompts'.
Each prompt will have a GUID for public identification, an integer ID for internal use, content, a display name, a list
of tags, an author, and timestamps.

The API will be divided into two sections: public and private. The public section will provide read-only access to a set
of public prompts without requiring a login. The private section, in addition to providing access to public prompts,
will allow users to create and edit their own personal prompts. Users will have the ability to browse through public,
private, or all prompts.

The API will be structured into four layers: database, service, core, and web.

1. The database layer, located in the `/data` directory, will use a repository pattern and MySQL. It will implement
   conversions between models and dictionaries for efficiency and use named parameters for SQL commands. The
   initialization logic will be contained in an `init.py` module.

2. The service layer, located in the `/service` directory, will handle requests for public and private prompts in
   separate modules. It will revalidate incoming models from the web layer using pydantic. All exceptions, whether they
   originate from the database or service layer, will be formatted as a `PromptException`.

3. The core layer, located in the `/core` directory, will focus on models and exceptions, all of which will
   extend `PromptException`.

4. The web layer, located in the `/web` directory, will contain separate resources for managing public and private
   prompts. It will use a dependency pattern to ensure that private resource methods require authentication. It will
   also incorporate a dependency for universal logging of all requests.

The API will support various functionalities through its endpoints. These include searching by text, tag,
classification, and requesting all prompts with pagination constraints. All responses will be in JSON format. After
login, users will have access to additional endpoints for adding, modifying, and deleting private prompts. These
endpoints will support Basic Authentication.

The API will also implement universal request logging in the
format `YYYY-MM-DD HH:min:sec,ms {{LoggingLevel}} {{request-id}} [thread-id] [method:line number] REQUEST START  (or REQUEST END)`.
The request-id will be generated from the host-datetime-threadid. All exceptions will be handled by a single exception
handler.
