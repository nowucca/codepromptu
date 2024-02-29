It seems like we would need the following use cases to make our API useful:
Remember that guids are used to identify prompts.

* Create a prompt.  When we create a prompt, we must specify a name and content for the prompt.

* Update a prompt's content.  Tags will be updated en-masse with all being deleted before the set of tags are inserted.

* Delete a prompt by guid which would remove it and any tags associated with it.

* Get a prompt by guid which would return the prompt and tags associated with it.  JSON format.

* Add a tag to a prompt
* Remove a tag from a prompt
* List all public prompts. JSON format, list of prompts.

All these operations can also be performed on private prompts after authentication has occurred.
We will use HTTP basic authentication against a known set of users.
