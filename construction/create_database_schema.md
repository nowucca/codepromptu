I think it might be time to consider how we are storing data in the database schema for this system:
```
{system description}
```
Let's generate a database schema that upholds requirements and conforms with Mysql that holds:
- all the necessary tables and indices for Prompt and searching for prompts
- a table called roster that has columns (user: string, password: string, class_key: string) to store user credentials_
- names all constraints
- every entity should have an id integer and a string guid. 
- At the start of the file set up for all tables to be properly fully internationalized
- Change all the names for constraints and indices.  
  - unique constraints should be named tablename_U{n} where N is an ordinal digit.  
  - Foreign constraints should be tablename_F{n}, and
  - indices should be named tablename_I{n} 

