from typing import List, Optional

import core
from core.exceptions import UnauthorizedError
from core.models import Prompt, PromptCreate, PromptUpdate, User
from data import get_current_db_context


class PromptRepositoryInterface:

    def create_prompt(self, prompt: PromptCreate, author: Optional[User] = None) -> str:
        # Implementation of the create-prompt use case
        pass

    def update_prompt(self, guid: str, prompt: PromptUpdate, user: Optional[User] = None) -> None:
        # Implementation of the update prompt use case
        pass

    def delete_prompt(self, guid: str, user: Optional[User] = None) -> None:
        # Implementation of the delete prompt use case
        pass

    def get_prompt(self, guid: str, user: Optional[User] = None) -> Prompt:
        # Implementation of the get prompt use case
        pass

    def get_prompt_by_name(self, name: str, user: Optional[User] = None) -> Prompt:
        # Implementation of the get prompt use case
        pass

    def add_tag_to_prompt(self, guid: str, tag: str, user: Optional[User] = None) -> None:
        # Implementation of the add tag to prompt use case
        pass

    def remove_tag_from_prompt(self, guid: str, tag: str, user: Optional[User] = None) -> None:
        # Implementation of the remove tag from prompt use case
        pass

    def list_prompts(self, user: Optional[User] = None) -> List[Prompt]:
        # Implementation of the list all public prompts use case
        pass
    def list_prompts_by_tags(self, tags_list: List[str], user: Optional[User] = None) -> List[Prompt]:
      # Implementation of the list all prompts use case by tags
        pass

class MySQLPromptRepository(PromptRepositoryInterface):

    def create_prompt(self, prompt: PromptCreate, author: Optional[User] = None) -> str:
        db = get_current_db_context()
        prompt_guid = core.make_guid()
        db.cursor.execute(
            "INSERT INTO prompts (guid, content, display_name, author) VALUES (%s, %s, %s, %s)",
            (prompt_guid, prompt.content, prompt.display_name, author.username if author else None)
        )
        self._update_tags(prompt_guid, prompt.tags, author)
        return prompt_guid

    def update_prompt(self, guid: str, prompt: PromptUpdate, user: Optional[User] = None) -> None:
        db = get_current_db_context()

        # Base SQL query
        sql = "UPDATE prompts "

        # Parameters for SQL query
        params = []

        setting_a_field = False
        # Iterate over the fields of the PromptUpdate object
        for field in prompt.dict(exclude_unset=True):

            # Skip the 'guid' and 'tags' fields
            if field not in ['guid', 'tags'] and getattr(prompt, field) is not None:
                # If this is the first field being updated, add the SET keyword
                if not setting_a_field:
                    sql += "SET "
                    setting_a_field = True
                sql += f"{field} = %s, "
                params.append(getattr(prompt, field))

        if not setting_a_field and prompt.tags is None:
            raise core.exceptions.DataValidationError("No fields to update")

        if setting_a_field:
            # Remove the trailing comma and space
            sql = sql.rstrip(', ')

            sql += " WHERE guid = %s"
            params.append(guid)

            db.cursor.execute(sql, params)

        # Update the tags if they are provided
        if prompt.tags is not None:
            self._update_tags(guid, prompt.tags, user)

    def delete_prompt(self, guid: str, user: Optional[User] = None) -> None:
        db = get_current_db_context()
        self._check_prompt_ownership(guid, user)
        db.cursor.execute(
            "DELETE FROM prompts WHERE guid = %s",
            (guid,)
        )
        self.remove_all_tags_from_prompt(guid, user)

    @staticmethod
    def _check_prompt_ownership(guid, user):
        db = get_current_db_context()
        query = "SELECT author FROM prompts WHERE guid = %s"
        params = (guid,)
        db.cursor.execute(query, params)
        author = db.cursor.fetchone()['author']
        if author != (user.username if user else None):
            raise UnauthorizedError(f"Attempting to update a prompt that does not belong to the user or is not NULL.")


    def get_prompt(self, guid: str, user: Optional[User] = None) -> Prompt:
        db = get_current_db_context()

        # Base SQL query
        sql = """
            SELECT prompts.*, GROUP_CONCAT(tags.tag) as tags 
            FROM prompts 
            LEFT JOIN prompt_tags ON prompts.id = prompt_tags.prompt_id 
            LEFT JOIN tags ON prompt_tags.tag_id = tags.id 
            WHERE prompts.guid = %s
        """

        # Parameters for SQL query
        params = [guid]

        # If user is not None, add the author clause and parameter
        if user is not None:
            sql += " AND prompts.author = %s"
            params.append(user.username)
        else:
            sql += " AND prompts.author IS NULL"

        sql += " GROUP BY prompts.id"

        db.cursor.execute(sql, params)
        result = db.cursor.fetchone()
        if result is None:
            raise core.exceptions.RecordNotFoundError("Prompt not found")

        # Access the result via column names
        result_dict = self.make_result_dict(result)

        return Prompt(**result_dict)

    @staticmethod
    def make_result_dict(result):
        result_dict = {
            "id": result["id"],
            "content": result["content"],
            "display_name": result["display_name"],
            "author": result["author"],
            "tags": result["tags"].split(',') if result["tags"] else [],  # Split tags by comma
            "guid": result["guid"],
            "created_at": result["created_at"],
            "updated_at": result["updated_at"],
        }
        return result_dict

    def get_prompt_by_name(self, name: str, user: Optional[User] = None) -> Prompt:
        db = get_current_db_context()

        # Base SQL query
        sql = """
            SELECT prompts.*, GROUP_CONCAT(tags.tag) as tags
            FROM prompts
            LEFT JOIN prompt_tags ON prompts.id = prompt_tags.prompt_id
            LEFT JOIN tags ON prompt_tags.tag_id = tags.id
            WHERE prompts.display_name = %s
        """

        # Parameters for SQL query
        params = [name]

        # If user is not None, add the author clause and parameter
        if user is not None:
            sql += " AND prompts.author = %s"
            params.append(user.username)
        else:
            sql += " AND prompts.author IS NULL"

        sql += " GROUP BY prompts.id"

        db.cursor.execute(sql, params)
        result = db.cursor.fetchone()
        if result is None:
            raise core.exceptions.RecordNotFoundError("Prompt not found")
        result_dict = self.make_result_dict(result)
        return Prompt(**result_dict)

    def add_tag_to_prompt(self, guid: str, tag: str, user: Optional[User] = None) -> None:
        db = get_current_db_context()
        db.cursor.execute("""
            INSERT INTO tags (tag) 
            VALUES (%s) AS new 
            ON DUPLICATE KEY UPDATE tag = new.tag
        """, (tag,))

        # Base SQL query
        sql = """
            INSERT INTO prompt_tags (prompt_id, tag_id) 
              SELECT prompts.id, tags.id 
                FROM prompts, tags 
               WHERE prompts.guid = %s AND tags.tag = %s
        """

        # Parameters for SQL query
        params = [guid, tag]

        # If user is not None, add the author clause and parameter
        if user is not None:
            sql += " AND prompts.author = %s"
            params.append(user.username)
        else:
            sql += " AND prompts.author IS NULL"

        db.cursor.execute(sql, params)

    def remove_tag_from_prompt(self, guid: str, tag: str, user: Optional[User] = None) -> None:
        db = get_current_db_context()

        # Base SQL query
        sql = """
            DELETE prompt_tags 
              FROM prompt_tags 
             INNER JOIN prompts ON prompt_tags.prompt_id = prompts.id 
             INNER JOIN tags ON prompt_tags.tag_id = tags.id 
             WHERE prompts.guid = %s AND tags.tag = %s
        """

        # Parameters for SQL query
        params = [guid, tag]

        # If user is not None, add the author clause and parameter
        if user is not None:
            sql += " AND prompts.author = %s"
            params.append(user.username)
        else:
            sql += " AND prompts.author IS NULL"

        db.cursor.execute(sql, params)

    def list_prompts(self, user: Optional[User] = None) -> List[Prompt]:
        db = get_current_db_context()

        # Base SQL query
        sql = """
            SELECT prompts.*, GROUP_CONCAT(tags.tag) as tags 
            FROM prompts 
            LEFT JOIN prompt_tags ON prompts.id = prompt_tags.prompt_id 
            LEFT JOIN tags ON prompt_tags.tag_id = tags.id 
        """

        # Parameters for SQL query
        params = []

        # If user is not None, add the author clause and parameter
        if user is not None:
            sql += " WHERE prompts.author = %s"
            params.append(user.username)
        else:
            sql += " WHERE prompts.author IS NULL"

        sql += " GROUP BY prompts.id"

        db.cursor.execute(sql, params)
        results = db.cursor.fetchall()
        return [Prompt(**self.make_result_dict(result)) for result in results]

    def list_prompts_by_tags(self, tags_list: List[str], user: Optional[User] = None) -> List[Prompt]:
        db = get_current_db_context()

        # Base SQL query
        sql = """
            SELECT prompts.*, GROUP_CONCAT(tags.tag) as tags
            FROM prompts
            LEFT JOIN prompt_tags ON prompts.id = prompt_tags.prompt_id
            LEFT JOIN tags ON prompt_tags.tag_id = tags.id
            WHERE tags.tag IN (%s)
        """ % ', '.join(['%s'] * len(tags_list))

        # Parameters for SQL query
        params = tags_list

        # If user is not None, add the author clause and parameter
        if user is not None:
            sql += " AND prompts.author = %s"
            params.append(user.username)
        else:
            sql += " AND prompts.author IS NULL"

        sql += " GROUP BY prompts.id"

        db.cursor.execute(sql, params)
        results = db.cursor.fetchall()
        return [Prompt(**self.make_result_dict(result)) for result in results]

    def _update_tags(self, guid: str, tags: List[str], user: Optional[User] = None) -> None:
        self._check_prompt_ownership(guid, user)
        # Remove all existing tags
        self.remove_all_tags_from_prompt(guid, user)
        # Add new tags
        for tag in tags:
            self.add_tag_to_prompt(guid, tag, user)

    def remove_all_tags_from_prompt(self, guid: str, user: Optional[User] = None) -> None:
        # Precondition: self._check_prompt_ownership(guid, user)
        db = get_current_db_context()
        params = [guid]
        remove_sql = """
            DELETE prompt_tags 
            FROM prompt_tags 
            INNER JOIN prompts ON prompt_tags.prompt_id = prompts.id 
            WHERE prompts.guid = %s
        """
        if user:
            remove_sql += " AND prompts.author = %s"
            params.append(user.username)
        else:
            remove_sql += " AND prompts.author IS NULL"

        db.cursor.execute(remove_sql, params)
