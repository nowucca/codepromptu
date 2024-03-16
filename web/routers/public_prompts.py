from fastapi import APIRouter, Depends, HTTPException
from fastapi.params import Query

from core.exceptions import RecordNotFoundError
from core.models import Prompt, User, PromptCreate, PromptUpdate
from service.prompt_service import PromptServiceInterface
from typing import List
from web.dependencies import get_prompt_service, require_admin_user
from urllib.parse import unquote_plus

router = APIRouter()


@router.post("/prompt/", status_code=201, summary="Add a new prompt. Requires an admin user.")
async def add_prompt(prompt: PromptCreate,
                     service: PromptServiceInterface = Depends(get_prompt_service),
                     _user: User = Depends(require_admin_user)):
    return service.create_prompt(prompt)


@router.delete("/prompt/{guid}", status_code=204, summary="Delete a prompt by GUID. Requires an admin user.")
def delete_prompt(guid: str,
                  service: PromptServiceInterface = Depends(get_prompt_service),
                  _user: User = Depends(require_admin_user)):
    service.delete_prompt(guid)
    return {}  # Return an empty response for 204 status


@router.put("/prompt/{guid}", status_code=204, summary="Update a prompt by GUID. Requires an admin user.")
def update_prompt(guid: str,
                  prompt: PromptUpdate,
                  service: PromptServiceInterface = Depends(get_prompt_service),
                  _user: User = Depends(require_admin_user)):
    service.update_prompt(guid, prompt)
    return {}  # Return an empty response for 204 status


@router.get("/prompt/{guid}", response_model=Prompt, summary="Retrieve a prompt by GUID.")
def get_prompt(guid: str, service: PromptServiceInterface = Depends(get_prompt_service)):
    try:
        return service.get_prompt(guid)
    except RecordNotFoundError:
        raise HTTPException(status_code=404, detail="Prompt not found")


@router.get("/prompt/name/{name}", response_model=Prompt, summary="Retrieve a prompt by name.")
def get_prompt_by_name(name: str, service: PromptServiceInterface = Depends(get_prompt_service)):
    try:
        decoded_name = unquote_plus(name)
        return service.get_prompt_by_name(decoded_name)
    except RecordNotFoundError:
        raise HTTPException(status_code=404, detail="Prompt not found")


@router.get("/prompt/", response_model=List[Prompt], summary="List all prompts.")
def list_prompts(skip: int = 0, limit: int = 10, service: PromptServiceInterface = Depends(get_prompt_service)):
    return service.list_prompts()[skip: skip + limit]

@router.get("/prompt/tags/",
            response_model=List[Prompt], summary="List Public Prompts by Tag")
def list_prompts_by_tag(
    tags: str = Query("", title="Tags", description="Comma-separated list of tags to search for"),
    service: PromptServiceInterface = Depends(get_prompt_service)):
    return service.list_prompts_by_tags(tags)

@router.post("/prompt/{guid}/tag/{tag}", status_code=204,
             summary="Add a tag to a prompt by GUID. Requires an admin user.")
def add_tag_to_prompt(guid: str, tag: str,
                      service: PromptServiceInterface = Depends(get_prompt_service),
                      _user: User = Depends(require_admin_user)):
    service.add_tag_to_prompt(guid, tag)
    return {}  # Return an empty response for 204 status


@router.delete("/prompt/{guid}/tag/{tag}", status_code=204,
               summary="Remove a tag from a prompt by GUID. Requires an admin user.")
def remove_tag_from_prompt(guid: str, tag: str,
                           service: PromptServiceInterface = Depends(get_prompt_service),
                           _user: User = Depends(require_admin_user)):
    service.remove_tag_from_prompt(guid, tag)
    return {}  # Return an empty response for 204 status
