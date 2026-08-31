"""认证测试：JWT 签发/验签一致性（与 Java 侧 JwtUtil 同算法同密钥）"""
import jwt

from app.config import get_settings


def _make_token(user_id: int) -> str:
    settings = get_settings()
    return jwt.encode(
        {"userId": user_id, "iss": settings.jwt_issuer, "aud": settings.jwt_user_audience,
         "exp": 4102444800},
        settings.user_jwt_secret, algorithm="HS256"
    )


def test_decode_valid_token():
    token = _make_token(4)
    payload = jwt.decode(
        token,
        get_settings().user_jwt_secret,
        algorithms=["HS256"],
        issuer=get_settings().jwt_issuer,
        audience=get_settings().jwt_user_audience,
    )
    assert payload["userId"] == 4


def test_reject_wrong_secret():
    token = _make_token(4)
    try:
        jwt.decode(token, "wrong-secret-value-with-at-least-32-characters", algorithms=["HS256"])
        assert False, "错误密钥不应验签通过"
    except jwt.InvalidTokenError:
        pass


def test_reject_expired_token():
    settings = get_settings()
    token = jwt.encode(
        {"userId": 4, "iss": settings.jwt_issuer, "aud": settings.jwt_user_audience, "exp": 0},
        settings.user_jwt_secret,
        algorithm="HS256",
    )
    try:
        jwt.decode(token, settings.user_jwt_secret, algorithms=["HS256"])
        assert False, "过期令牌不应验签通过"
    except jwt.ExpiredSignatureError:
        pass
