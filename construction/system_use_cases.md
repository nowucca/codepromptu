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
