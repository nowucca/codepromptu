Lets build the service layer for the following system and database schema.
Let's use the repository pattern with interfaces, models, and exceptions.

The system description is as follows:
```
{system-description?}
```

Let's make sure to cover the following use cases for our system:
```
{system-usecase-summary?}
```

Here are the database repository interfaces to use:
```
{database-repository-interfaces?}
```

Here are the core model objects to use:
```
{model-objects?}
```

Here are the exceptions to use:
```
{exceptions?}
```

The service layer is responsible for transaction management and business logic and validation.
Create the service layer interface, then implement it.

Assume we have the following DatabaseContext class:
```
{database-context?}

```

Let's generate:
* a list of files needed to cleanly implement the service interface and implementation
* complete code for the service layer
* implement Pydantic object violations for all public function signatures and return appropriate core exceptions
* each major service method should start and manage a transaction using the DatabaseContext class
* specify types and salient docstrings for all public function signatures
- Don't forget to separate initialisation code into service/init.py
- Let's assume use of python-dotenv to read in configuration items
- Include a sample .env file with any configuration items needed

Make a XServiceInterface class and a XService class for each of the following:
PromptService
UserService

The way we intend to use DatabaseContext is as follows:
 with DatabaseContext() as db:
            try:
                db.begin_transaction()
                # do some work
                db.commit_transaction()
                # return result
            except PromptException as known_exc:
                traceback.print_exc()
                db.rollback_transaction()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                db.rollback_transaction()
                raise PromptException("An unexpected error occurred while processing your request.") from e

```