Lets build the web layer for the following system and service layer.
Let's use FastAPI to do this.

The system description is as follows:
```
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

```

Let's make sure to cover the following use cases for our system:
```
Here are the system use cases for the prompt management system:

1. **Create a Prompt**: This use case involves creating a new prompt. The user must provide a name and content for the prompt. The system will generate a GUID for the prompt and store it in the database. The system will return the GUID of the newly created prompt.

2. **Update a Prompt**: This use case involves updating the content of an existing prompt. The user must provide the GUID of the prompt they wish to update, along with the new content. The system will update the content of the prompt in the database. If tags are associated with the prompt, they will be deleted and reinserted.

3. **Delete a Prompt**: This use case involves deleting an existing prompt. The user must provide the GUID of the prompt they wish to delete. The system will remove the prompt and any associated tags from the database.

4. **Get a Prompt**: This use case involves retrieving a prompt by its GUID. The user must provide the GUID of the prompt they wish to retrieve. The system will return the prompt and any associated tags in JSON format.

5. **Add a Tag to a Prompt**: This use case involves adding a tag to an existing prompt. The user must provide the GUID of the prompt and the tag they wish to add. The system will associate the tag with the prompt in the database.

6. **Remove a Tag from a Prompt**: This use case involves removing a tag from an existing prompt. The user must provide the GUID of the prompt and the tag they wish to remove. The system will disassociate the tag from the prompt in the database.

7. **List all Public Prompts**: This use case involves retrieving all public prompts. The system will return a list of all public prompts in JSON format.

8. **Authentication**: This use case involves authenticating a user. The user must provide their credentials. The system will verify the credentials and, if they are valid, allow the user to perform operations on private prompts.

All these operations can also be performed on private prompts after authentication has occurred. The system will use HTTP basic authentication against a known set of users.

```

Here is the prompt service interface to use:
```
class PromptServiceInterface:
    def create_prompt(self, prompt: PromptCreate) -> str:
        """
        Creates a new prompt in the database.

        Args:
            prompt (PromptCreate): The prompt to create.

        Returns:
            str: The GUID of the created prompt.

        Raises:
            ConstraintViolationError: If the prompt data is invalid.
            PromptException: If an unexpected error occurs.
        """
        pass

    def update_prompt(self, prompt: PromptUpdate) -> None:
        """
        Updates the content of an existing prompt.

        Args:
            prompt (PromptUpdate): The prompt to update, which includes the GUID and the new content.

        Raises:
            ConstraintViolationError: If the prompt data is invalid.
            PromptException: If an unexpected error occurs.
        """
        pass

    def delete_prompt(self, guid: str) -> None:
        """
        Deletes an existing prompt.

        Args:
            guid (str): The GUID of the prompt to delete.

        Raises:
            PromptException: If an unexpected error occurs.
        """
        pass

    def get_prompt(self, guid: str) -> Prompt:
        """
        Retrieves a prompt by its GUID.

        Args:
            guid (str): The GUID of the prompt to retrieve.

        Returns:
            Prompt: The retrieved prompt.

        Raises:
            PromptException: If an unexpected error occurs.
        """
        pass

    def add_tag_to_prompt(self, guid: str, tag: str) -> None:
        """
        Adds a tag to an existing prompt.

        Args:
            guid (str): The GUID of the prompt to add the tag to.
            tag (str): The tag to add.

        Raises:
            PromptException: If an unexpected error occurs.
        """
        pass

    def remove_tag_from_prompt(self, guid: str, tag: str) -> None:
        """
        Removes a tag from an existing prompt.

        Args:
            guid (str): The GUID of the prompt to remove the tag from.
            tag (str): The tag to remove.

        Raises:
            PromptException: If an unexpected error occurs.
        """
        pass

    def list_all_public_prompts(self) -> List[Prompt]:
        """
        Retrieves all public prompts.

        Returns:
            List[Prompt]: A list of all public prompts.

        Raises:
            PromptException: If an unexpected error occurs.
        """
        pass
        
# service/user_service.py
import traceback

from pydantic import ValidationError

from core.exceptions import PromptException, ConstraintViolationError
from core.models import User
from data import DatabaseContext
from data.user_repository import UserRepositoryInterface


class UserServiceInterface:
    def authenticate_user(self, username: str, password: str) -> bool:
        pass


class UserService(UserServiceInterface):
    def __init__(self, user_repository: UserRepositoryInterface):
        self.user_repository = user_repository

    def authenticate_user(self, username: str, password: str) -> bool:
        try:
            user = User(user=username, password=password)
        except ValidationError as e:
            raise ConstraintViolationError(str(e))

        with DatabaseContext():
            try:
                is_authenticated = self.user_repository.authenticate_user(user.user, user.password)
                return is_authenticated
            except PromptException as known_exc:
                traceback.print_exc()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                raise PromptException("An unexpected error occurred while processing your request.") from e
        
```

Here are the core model objects to use:
```
from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel


class PromptCreate(BaseModel):
    content: str
    display_name: str
    author: Optional[int] = None
    tags: Optional[List[str]] = []


class PromptUpdate(PromptCreate):
    guid: str


class Prompt(PromptUpdate):
    id: int
    created_at: datetime
    updated_at: datetime


class User(BaseModel):
    user: str
    password: str
    class_key: str

```

Here are the core exceptions to use:
```
class PromptException(Exception):
    def __init__(self, message: str):
        self.message = message
        super().__init__(self.message)


class DBConnectionError(PromptException):
    def __init__(self, message="Failed to connect to the database."):
        super().__init__(message)


class RecordNotFoundError(PromptException):
    def __init__(self, message="The requested record was not found."):
        super().__init__(message)


class ConstraintViolationError(PromptException):
    def __init__(self, message="Database constraint was violated."):
        super().__init__(message)

# 2. Service Layer Exceptions

class DataValidationError(PromptException):
    def __init__(self, message="Provided data is invalid."):
        super().__init__(message)


class UnauthorizedError(PromptException):
    def __init__(self, message="Unauthorized access."):
        super().__init__(message)


class OperationNotAllowedError(PromptException):
    def __init__(self, message="This operation is not allowed."):
        super().__init__(message)


# 3. Web Layer Exceptions

class BadRequestError(PromptException):
    def __init__(self, message="Bad request data."):
        super().__init__(message)


class EndpointNotFoundError(PromptException):
    def __init__(self, message="Endpoint not found."):
        super().__init__(message)


class AuthenticationError(PromptException):
    def __init__(self, message="Authentication failed."):
        super().__init__(message)

EXCEPTION_STATUS_CODES = {
    DataValidationError: 400,       # Bad Request
    ConstraintViolationError: 409,  # Conflict
    PromptException: 500,           # Internal Server Error (Generic fallback)
    DBConnectionError: 500,         # Internal Server Error (Generic fallback)
    RecordNotFoundError: 404,       # Not Found
    UnauthorizedError: 401,         # Unauthorized
    OperationNotAllowedError: 403,  # Forbidden
    BadRequestError: 400,           # Bad Request
    EndpointNotFoundError: 404,     # Not Found
    AuthenticationError: 401,       # Unauthorized
}

```

The web layer is responsible for validation, central exception handling with a single exception handler,
and logging of each request (assigning a request id, logging the start and end result of each request per above).

Let's create a FastAPI application with a router for public prompts and a router for private prompts.
The public router should support read-only access to prompts before login.
The private router should support read-write access to prompts after login.

Let's use HTTP basic authentication for the private router, and implement a service layer repository and database level repository for user validation.
Let's write a global dependency so all endpoints are logged using the required format.

Let's generate a file at a time and pause to think upfront about how things will all fit together.

Then let's generate an API description with enough detail to write test cases for the web layer.
