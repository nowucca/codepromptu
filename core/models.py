from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel


class PromptCreate(BaseModel):
    content: str
    display_name: str
    author: Optional[str] = None
    tags: Optional[List[str]] = []


class PromptUpdate(PromptCreate):
    guid: str


class Prompt(PromptUpdate):
    id: int
    created_at: datetime
    updated_at: datetime


class User(BaseModel):
    username: str
    password: str
    class_key: str
