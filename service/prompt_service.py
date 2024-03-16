# service/prompt_service.py
import traceback
from typing import List, Optional

from pydantic import ValidationError

from core.exceptions import PromptException, ConstraintViolationError, DataValidationError
from core.models import Prompt, PromptCreate, PromptUpdate, User
from data import DatabaseContext
from data.prompt_repository import PromptRepositoryInterface


class PromptServiceInterface:
    def create_prompt(self, prompt: PromptCreate, author: Optional[User] = None) -> str:
        """
        Creates a new prompt in the database.

        Args:
            prompt (PromptCreate): The prompt to create.

        Returns:
            str: The GUID of the created prompt.

        Raises:
            ConstraintViolationError: If the prompt data is invalid.
            PromptException: If an unexpected error occurs.
            :param prompt:
            :param author:
        """
        pass

    def update_prompt(self, guid:str, prompt: PromptUpdate, user: Optional[User] = None) -> None:
        """
        Updates the content of an existing prompt.

        Args:
            prompt (PromptUpdate): The prompt to update, which includes the GUID and the new content.

        Raises:
            ConstraintViolationError: If the prompt data is invalid.
            PromptException: If an unexpected error occurs.
            :param guid:
            :param prompt:
            :param user:
        """
        pass

    def delete_prompt(self, guid: str, user: Optional[User] = None) -> None:
        """
        Deletes an existing prompt.

        Args:
            guid (str): The GUID of the prompt to delete.

        Raises:
            PromptException: If an unexpected error occurs.
            :param guid:
            :param user:
        """
        pass

    def get_prompt(self, guid: str, user: Optional[User] = None) -> Prompt:
        """
        Retrieves a prompt by its GUID.

        Args:
            guid (str): The GUID of the prompt to retrieve.

        Returns:
            Prompt: The retrieved prompt.

        Raises:
            PromptException: If an unexpected error occurs.
            :param guid:
            :param user:
        """
        pass

    def get_prompt_by_name(self, name: str, user: Optional[User] = None) -> Prompt:
        """
        Retrieves a prompt by its display name.

        Args:
            name (str): The name of the prompt to retrieve.

        Returns:
            Prompt: The retrieved prompt.

        Raises:
            PromptException: If an unexpected error occurs.
            :param name:
            :param user:
        """
        pass

    def add_tag_to_prompt(self, guid: str, tag: str, user: Optional[User] = None) -> None:
        """
        Adds a tag to an existing prompt.

        Args:
            guid (str): The GUID of the prompt to add the tag to.
            tag (str): The tag to add.

        Raises:
            PromptException: If an unexpected error occurs.
            :param tag:
            :param guid:
            :param user:
        """
        pass

    def remove_tag_from_prompt(self, guid: str, tag: str, user: Optional[User] = None) -> None:
        """
        Removes a tag from an existing prompt.

        Args:
            guid (str): The GUID of the prompt to remove the tag from.
            tag (str): The tag to remove.

        Raises:
            PromptException: If an unexpected error occurs.
            :param tag:
            :param guid:
            :param user:
        """
        pass

    def list_prompts(self, user: Optional[User] = None) -> List[Prompt]:
        """
        Retrieves all prompts (public if there is no user), or private if there is a user provided.

        Returns:
            List[Prompt]: A list of all prompts.

        Raises:
            PromptException: If an unexpected error occurs.
        """
        pass

    def list_prompts_by_tags(self, tags: str, user: Optional[User] = None) -> List[Prompt]:
        """
        Retrieves all prompts that have at least one of the tags in the provided list.
        (returns public prompts if there is no user, or private if there is a user provided)

        Args:
            tags Comma separated list of tags to filter by.

        Returns:
            List[Prompt]: A list of all prompts that have at least one of the tags in the provided list.

        Raises:
            PromptException: If an unexpected error occurs.
        """
        pass

class PromptService(PromptServiceInterface):
    def __init__(self, prompt_repository: PromptRepositoryInterface):
        self.prompt_repository = prompt_repository

    def create_prompt(self, prompt: PromptCreate, author: Optional[User] = None) -> str:
        try:
            prompt = PromptCreate(**prompt.dict())
        except ValidationError as e:
            raise ConstraintViolationError(str(e))

        with DatabaseContext() as db:
            try:
                db.begin_transaction()
                guid = self.prompt_repository.create_prompt(prompt, author)
                db.commit_transaction()
                return guid
            except PromptException as known_exc:
                traceback.print_exc()
                db.rollback_transaction()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                db.rollback_transaction()
                raise PromptException("An unexpected error occurred while processing your request.") from e

    def update_prompt(self, guid: str, prompt: PromptUpdate, user: Optional[User] = None) -> None:
        try:
            prompt = PromptUpdate(**prompt.dict())
        except ValidationError as e:
            raise ConstraintViolationError(str(e))

        with DatabaseContext() as db:
            try:
                db.begin_transaction()
                self.prompt_repository.update_prompt(guid, prompt, user)
                db.commit_transaction()
            except PromptException as known_exc:
                traceback.print_exc()
                db.rollback_transaction()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                db.rollback_transaction()
                raise PromptException("An unexpected error occurred while processing your request.") from e

    def delete_prompt(self, guid: str, user: Optional[User] = None) -> None:
        with DatabaseContext() as db:
            try:
                db.begin_transaction()
                self.prompt_repository.delete_prompt(guid, user)
                db.commit_transaction()
            except PromptException as known_exc:
                traceback.print_exc()
                db.rollback_transaction()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                db.rollback_transaction()
                raise PromptException("An unexpected error occurred while processing your request.") from e

    def get_prompt(self, guid: str, user: Optional[User] = None) -> Prompt:
        with DatabaseContext():
            try:
                return self.prompt_repository.get_prompt(guid, user)
            except PromptException as known_exc:
                traceback.print_exc()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                raise PromptException("An unexpected error occurred while processing your request.") from e

    def get_prompt_by_name(self, name: str, user: Optional[User] = None) -> Prompt:
        with DatabaseContext():
            try:
                return self.prompt_repository.get_prompt_by_name(name, user)
            except PromptException as known_exc:
                traceback.print_exc()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                raise PromptException("An unexpected error occurred while processing your request.") from e

    def add_tag_to_prompt(self, guid: str, tag: str, user: Optional[User] = None) -> None:
        with DatabaseContext() as db:
            try:
                db.begin_transaction()
                self.prompt_repository.add_tag_to_prompt(guid, tag, user)
                db.commit_transaction()
            except PromptException as known_exc:
                traceback.print_exc()
                db.rollback_transaction()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                db.rollback_transaction()
                raise PromptException("An unexpected error occurred while processing your request.") from e

    def remove_tag_from_prompt(self, guid: str, tag: str, user: Optional[User] = None) -> None:
        with DatabaseContext() as db:
            try:
                db.begin_transaction()
                self.prompt_repository.remove_tag_from_prompt(guid, tag, user)
                db.commit_transaction()
            except PromptException as known_exc:
                traceback.print_exc()
                db.rollback_transaction()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                db.rollback_transaction()
                raise PromptException("An unexpected error occurred while processing your request.") from e

    def list_prompts(self, user: Optional[User] = None) -> List[Prompt]:
        with DatabaseContext():
            try:
                return self.prompt_repository.list_prompts(user)
            except PromptException as known_exc:
                traceback.print_exc()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                raise PromptException("An unexpected error occurred while processing your request.") from e

    def list_prompts_by_tags(self, tags: str, user: Optional[User] = None) -> List[Prompt]:
        with DatabaseContext():
            try:
                tags_list = tags.split(',')

                if not tags_list:
                    raise DataValidationError("No tags provided.")

                return self.prompt_repository.list_prompts_by_tags(tags_list, user)
            except PromptException as known_exc:
                traceback.print_exc()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                raise PromptException("An unexpected error occurred while processing your request.") from e
