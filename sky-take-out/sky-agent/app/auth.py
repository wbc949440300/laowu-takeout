"""JWT authentication dependencies for user and administrator APIs."""
import jwt
from fastapi import Header, HTTPException
from app.config import get_settings

class CurrentUser:
    def __init__(self, user_id: int, token: str):
        self.user_id = user_id
        self.token = token

class CurrentAdmin:
    def __init__(self, admin_id: int, token: str):
        self.admin_id = admin_id
        self.token = token

def get_current_user(authentication: str = Header(default="")) -> CurrentUser:
    if not authentication:
        raise HTTPException(status_code=401, detail="missing authentication token")
    try:
        settings = get_settings()
        payload = jwt.decode(authentication, settings.user_jwt_secret, algorithms=["HS256"], issuer=settings.jwt_issuer, audience=settings.jwt_user_audience, options={"require": ["exp", "iss", "aud", "userId"]})
        user_id = payload.get("userId")
        if user_id is None:
            raise HTTPException(status_code=401, detail="invalid token")
        return CurrentUser(user_id=int(user_id), token=authentication)
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="login expired")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="invalid token")

def get_current_admin(token: str = Header(default="")) -> CurrentAdmin:
    if not token:
        raise HTTPException(status_code=401, detail="missing admin token")
    try:
        settings = get_settings()
        payload = jwt.decode(token, settings.admin_jwt_secret, algorithms=["HS256"], issuer=settings.jwt_issuer, audience=settings.jwt_admin_audience, options={"require": ["exp", "iss", "aud", "empId"]})
        admin_id = payload.get("empId")
        if admin_id is None:
            raise HTTPException(status_code=401, detail="invalid token")
        return CurrentAdmin(admin_id=int(admin_id), token=token)
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="login expired")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="invalid token")