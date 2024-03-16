from fastapi import APIRouter, Depends, HTTPException
from fastapi.params import Query

from core.exceptions import RecordNotFoundError
from core.models import Prompt, User, PromptCreate, PromptUpdate
from service.prompt_service import PromptServiceInterface
from typing import List
from web.dependencies import require_current_user, get_prompt_service

router = APIRouter()


@router.post("/prompt/", status_code=201, summary="Add a new private prompt. Requires a logged-in user.")
async def add_prompt(prompt: PromptCreate,
                     service: PromptServiceInterface = Depends(get_prompt_service),
                     user: User = Depends(require_current_user)):
    return service.create_prompt(prompt, user)


@router.delete("/prompt/{guid}", status_code=204,
               summary="Delete a private prompt by GUID. Requires the owner of the prompt.")
def delete_prompt(guid: str,
                  service: PromptServiceInterface = Depends(get_prompt_service),
                  user: User = Depends(require_current_user)):
    service.delete_prompt(guid, user)
    return {}  # Return an empty response for 204 status


@router.put("/prompt/{guid}", status_code=204,
            summary="Update a private prompt by GUID. Requires the owner of the prompt.")
def update_prompt(guid: str,
                  prompt: PromptUpdate,
                  service: PromptServiceInterface = Depends(get_prompt_service),
                  user: User = Depends(require_current_user)):
    service.update_prompt(guid, prompt, user)
    return {}  # Return an empty response for 204 status


@router.get("/prompt/{guid}", response_model=Prompt, summary="Retrieve a private prompt by GUID.")
def get_prompt(guid: str, service: PromptServiceInterface = Depends(get_prompt_service),
               user: User = Depends(require_current_user)):
    try:
        return service.get_prompt(guid, user)
    except RecordNotFoundError:
        raise HTTPException(status_code=404, detail="Prompt not found")


@router.get("/prompt/name/{name}", response_model=Prompt, summary="Retrieve a private prompt by name.")
def get_prompt_by_name(name: str, service: PromptServiceInterface = Depends(get_prompt_service),
                       user: User = Depends(require_current_user)):
    try:
        return service.get_prompt_by_name(name, user)
    except RecordNotFoundError:
        raise HTTPException(status_code=404, detail="Prompt not found")


@router.get("/prompt/", response_model=List[Prompt], summary="List all private prompts of the logged-in user.")
def list_prompts(skip: int = 0, limit: int = 10,
                 service: PromptServiceInterface = Depends(get_prompt_service),
                 user: User = Depends(require_current_user)):
    return service.list_prompts(user)[skip: skip + limit]

@router.get("/prompt/tags/", response_model=List[Prompt], summary="List Private Prompts by Tag")
async def list_prompts_by_tag(tags: str = Query("", title="Tags", description="Comma-separated list of tags to search for"),
                             service: PromptServiceInterface = Depends(get_prompt_service),
                             user: User = Depends(require_current_user)):
    list = service.list_prompts_by_tags(tags, user)
    return list
@router.post("/prompt/{guid}/tag/{tag}", status_code=204,
             summary="Add a tag to a private prompt by GUID. Requires the owner of the prompt.")
def add_tag_to_prompt(guid: str, tag: str,
                      service: PromptServiceInterface = Depends(get_prompt_service),
                      user: User = Depends(require_current_user)):
    service.add_tag_to_prompt(guid, tag, user)
    return {}  # Return an empty response for 204 status


@router.delete("/prompt/{guid}/tag/{tag}", status_code=204,
               summary="Remove a tag from a private prompt by GUID. Requires the owner of the prompt.")
def remove_tag_from_prompt(guid: str, tag: str,
                           service: PromptServiceInterface = Depends(get_prompt_service),
                           user: User = Depends(require_current_user)):
    service.remove_tag_from_prompt(guid, tag, user)
    return {}  # Return an empty response for 204 status
