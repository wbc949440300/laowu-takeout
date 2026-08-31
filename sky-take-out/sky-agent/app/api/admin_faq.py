from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.auth import CurrentAdmin, get_current_admin
from app.rag.retriever import create_faq, delete_faq, list_faq, update_faq

router = APIRouter(prefix="/admin/faq", tags=["admin-faq"])


class FAQPayload(BaseModel):
    question: str = Field(..., min_length=1, max_length=200)
    answer: str = Field(..., min_length=1, max_length=5000)


@router.get("")
def get_faq(admin: CurrentAdmin = Depends(get_current_admin)):
    records = list_faq()
    return {"records": records, "total": len(records)}


@router.post("")
def add_faq(payload: FAQPayload, admin: CurrentAdmin = Depends(get_current_admin)):
    try:
        return create_faq(payload.question, payload.answer)
    except ValueError as exc:
        raise HTTPException(status_code=409 if "already exists" in str(exc) else 400, detail=str(exc))


@router.put("/{entry_id}")
def edit_faq(entry_id: str, payload: FAQPayload, admin: CurrentAdmin = Depends(get_current_admin)):
    try:
        return update_faq(entry_id, payload.question, payload.answer)
    except KeyError:
        raise HTTPException(status_code=404, detail="FAQ not found")
    except ValueError as exc:
        raise HTTPException(status_code=409 if "already exists" in str(exc) else 400, detail=str(exc))


@router.delete("/{entry_id}")
def remove_faq(entry_id: str, admin: CurrentAdmin = Depends(get_current_admin)):
    try:
        delete_faq(entry_id)
    except KeyError:
        raise HTTPException(status_code=404, detail="FAQ not found")
    return {"deleted": True}
