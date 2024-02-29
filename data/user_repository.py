import traceback
from typing import Optional

from data import get_current_db_context
from core.models import User
from mysql.connector import Error


class UserRepositoryInterface:
    def __init__(self):
        pass

    def authenticate_user(self, username: str) -> Optional[User]:
        # Implementation of the authenticate user use case
        pass


class MySQLUserRepository(UserRepositoryInterface):

    def authenticate_user(self, username: str) -> Optional[User]:
        db = get_current_db_context()
        db.cursor.execute(
            "SELECT * FROM roster WHERE user = %s",
            (username,)
        )
        try:
            result = db.cursor.fetchone()
            if result is not None:
                # Assuming the User model has 'username' and 'password' attributes
                # and the 'roster' table has 'user' and 'password' columns
                return User(username=result['user'], password=result['password'], class_key=result['class_key'])
            else:
                return None
        except Error:
            # Log the exception
            traceback.print_exc()
            return None
