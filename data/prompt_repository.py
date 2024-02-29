from typing import List, Optional

import core
from core.exceptions import UnauthorizedError
from core.models import Prompt, PromptCreate, PromptUpdate, User
from data import get_current_db_context


class PromptRepositoryInterface:

    def create_prompt(self, prompt: PromptCreate, author: Optional[User] = None) -> str:
        # Implementation of the create-prompt use case
        pass

    def update_prompt(self, prompt: PromptUpdate, user: Optional[User] = None) -> None:
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


class MySQLPromptRepository(PromptRepositoryInterface):

    def create_prompt(self, prompt: PromptCreate, author: Optional[User] = None) -> str:
        db = get_current_db_context()
        prompt_guid = core.make_guid()
        db.cursor.execute(
            "INSERT INTO prompts (guid, content, display_name, author) VALUES (%s, %s, %s, %s)",
            (prompt_guid, prompt.content, prompt.display_name, author.username if author else None)
        )
        self._update_tags(prompt_guid, prompt.tags)
        return prompt.guid

    def update_prompt(self, prompt: PromptUpdate, user: Optional[User] = None) -> None:
        db = get_current_db_context()
        db.cursor.execute(
            "UPDATE prompts SET content = %s, display_name = %s, author = %s WHERE guid = %s",
            (prompt.content, prompt.display_name, user.username if user else None, prompt.guid)
        )
        self._update_tags(prompt.guid, prompt.tags)

    def delete_prompt(self, guid: str, user: Optional[User] = None) -> None:
        db = get_current_db_context()
        self._check_prompt_ownership(guid, user)
        db.cursor.execute(
            "DELETE FROM prompts WHERE guid = %s",
            (guid,)
        )
        self.remove_all_tags_from_prompt(guid)

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

        db.cursor.execute("""
            SELECT prompts.*, GROUP_CONCAT(tags.tag) as tags 
              FROM prompts 
              LEFT JOIN prompt_tags ON prompts.id = prompt_tags.prompt_id 
              LEFT JOIN tags ON prompt_tags.tag_id = tags.id 
              WHERE prompts.guid = %s 
                AND prompts.author = %s
              GROUP BY prompts.id
        """, (guid, user.username if user else None))
        result = db.cursor.fetchone()
        if result is None:
            raise core.exceptions.RecordNotFoundError("Prompt not found")
        return Prompt(*result)  # type: ignore

    def get_prompt_by_name(self, name: str, user: Optional[User] = None) -> Prompt:
        db = get_current_db_context()

        db.cursor.execute("""
                    SELECT prompts.*, GROUP_CONCAT(tags.tag) as tags 
                      FROM prompts 
                      LEFT JOIN prompt_tags ON prompts.id = prompt_tags.prompt_id 
                      LEFT JOIN tags ON prompt_tags.tag_id = tags.id 
                      WHERE prompts.display_name = %s 
                        AND prompts.author = %s
                      GROUP BY prompts.id
                """, (name, user.username if user else None))
        result = db.cursor.fetchone()
        if result is None:
            raise core.exceptions.RecordNotFoundError("Prompt not found")
        return Prompt(*result)  # type: ignore

    def add_tag_to_prompt(self, guid: str, tag: str, user: Optional[User] = None) -> None:
        db = get_current_db_context()
        db.cursor.execute("""
            INSERT INTO tags (tag) VALUES (%s) ON DUPLICATE KEY UPDATE tag = VALUES(tag)
        """, (tag,))
        db.cursor.execute("""
            INSERT INTO prompt_tags (prompt_id, tag_id) 
              SELECT prompts.id, tags.id 
                FROM prompts, tags 
               WHERE prompts.guid = %s AND tags.tag = %s
                 AND prompts.author = %s
        """, (guid, tag, user.username if user else None))

    def remove_tag_from_prompt(self, guid: str, tag: str, user: Optional[User] = None) -> None:
        db = get_current_db_context()
        db.cursor.execute("""
            DELETE prompt_tags 
              FROM prompt_tags 
             INNER JOIN prompts ON prompt_tags.prompt_id = prompts.id 
             INNER JOIN tags ON prompt_tags.tag_id = tags.id 
             WHERE prompts.guid = %s AND tags.tag = %s AND prompts.author = %s",
        """, (guid, tag, user.username if user else None))

    def list_prompts(self, user: Optional[User] = None) -> List[Prompt]:
        db = get_current_db_context()
        db.cursor.execute("""
            SELECT prompts.*, GROUP_CONCAT(tags.tag) as tags 
              FROM prompts 
              LEFT JOIN prompt_tags ON prompts.id = prompt_tags.prompt_id 
              LEFT JOIN tags ON prompt_tags.tag_id = tags.id 
              WHERE prompts.author = %s
              GROUP BY prompts.id
        """, (user.username if user else None))
        results = db.cursor.fetchall()
        return [Prompt(*result) for result in results]  # type: ignore

    def _update_tags(self, guid: str, tags: List[str]) -> None:
        # Remove all existing tags
        self.remove_all_tags_from_prompt(guid)
        # Add new tags
        for tag in tags:
            self.add_tag_to_prompt(guid, tag)

    @staticmethod
    def remove_all_tags_from_prompt(guid: str, user: Optional[User] = None) -> None:
        db = get_current_db_context()
        db.cursor.execute("""
            DELETE prompt_tags 
            FROM prompt_tags 
            INNER JOIN prompts ON prompt_tags.prompt_id = prompts.id 
            WHERE prompts.guid = %s
              AND prompts.author = %s
        """, (guid, user.username if user else None))
