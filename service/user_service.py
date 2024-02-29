# service/user_service.py
import traceback
from typing import Optional

from core.exceptions import PromptException
from core.models import User
from data import DatabaseContext
from data.user_repository import UserRepositoryInterface


class UserServiceInterface:
    def authenticate_user(self, username: str) -> User:
        pass


class UserService(UserServiceInterface):
    def __init__(self, user_repository: UserRepositoryInterface):
        self.user_repository = user_repository


    def authenticate_user(self, username: str) -> Optional[User]:
        with DatabaseContext():
            try:
                user = self.user_repository.authenticate_user(username)
                return user
            except PromptException as known_exc:
                traceback.print_exc()
                raise known_exc
            except Exception as e:
                traceback.print_exc()
                raise PromptException("An unexpected error occurred while processing your request.") from e
