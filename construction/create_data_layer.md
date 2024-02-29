Lets build the data layer for the following system and database schema.
Let's use DB-API to do this with a MYSQL backend.
The system description is as follows:
```
{system-description?}
```

Here is the database schema to use:
```
{schema-description?}
```
Let's generate:
* a list of files needed to cleanly implement the provided Mysql version of the database
* complete code for the data layer using a repository pattern with interfaces, models, and exceptions

- Don't forget to separate initialisation code into data/init.py
- Let's assume use of python-dotenv to read in configuration items 
- Include a sample .env file with any configuration items needed
  - specify types and salient docstrings for all public function signatures
Let's make sure to cover the following use cases for our system:
```
{system-usecase-summary?}
```
