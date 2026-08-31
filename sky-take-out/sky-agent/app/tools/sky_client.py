"""Java 后端（老吴外卖）API 客户端：透传用户 JWT + traceId 串联链路"""
import uuid

import httpx

from app.config import get_settings


_shared_http_client: httpx.AsyncClient | None = None


async def init_sky_http_client() -> None:
    """Create the process-wide connection pool during FastAPI startup."""
    global _shared_http_client
    if _shared_http_client is not None:
        return
    settings = get_settings()
    timeout = httpx.Timeout(
        connect=settings.sky_http_connect_timeout,
        read=settings.sky_http_read_timeout,
        write=settings.sky_http_read_timeout,
        pool=settings.sky_http_connect_timeout,
    )
    limits = httpx.Limits(
        max_connections=settings.sky_http_max_connections,
        max_keepalive_connections=settings.sky_http_max_keepalive_connections,
    )
    _shared_http_client = httpx.AsyncClient(
        timeout=timeout, limits=limits, transport=httpx.AsyncHTTPTransport(retries=1)
    )


async def close_sky_http_client() -> None:
    global _shared_http_client
    if _shared_http_client is not None:
        await _shared_http_client.aclose()
        _shared_http_client = None


class SkyApiError(Exception):
    """业务失败：携带后端返回的 msg 与结构化 code"""

    def __init__(
        self, msg: str, code: int | str = 0, retryable: bool = False, trace_id: str | None = None
    ):
        super().__init__(msg)
        self.msg = msg
        self.code = code
        self.retryable = retryable
        self.trace_id = trace_id


class SkyClient:
    def __init__(
        self, token: str, trace_id: str | None = None, http_client: httpx.AsyncClient | None = None,
        auth_header: str = "authentication"
    ):
        self._settings = get_settings()
        self._token = token
        self._trace_id = trace_id or uuid.uuid4().hex
        self._http_client = http_client
        self._auth_header = auth_header

    @property
    def trace_id(self) -> str:
        return self._trace_id

    def _headers(self) -> dict:
        return {
            self._auth_header: self._token,
            "X-Trace-Id": self._trace_id,
        }

    async def _request(self, method: str, path: str, **kwargs) -> dict:
        url = f"{self._settings.sky_server_base_url}{path}"
        client = self._http_client or _shared_http_client
        if client is None:
            raise SkyApiError(
                "Agent HTTP 客户端尚未初始化", code="CLIENT_NOT_READY", retryable=True, trace_id=self._trace_id
            )
        try:
            resp = await client.request(method, url, headers=self._headers(), **kwargs)
        except httpx.TimeoutException as exc:
            raise SkyApiError(
                "后端请求超时，请稍后重试", code="UPSTREAM_TIMEOUT", retryable=True, trace_id=self._trace_id
            ) from exc
        except httpx.NetworkError as exc:
            raise SkyApiError(
                "后端服务暂时不可用，请稍后重试", code="UPSTREAM_UNAVAILABLE", retryable=True, trace_id=self._trace_id
            ) from exc
        if resp.status_code == 401:
            raise SkyApiError("登录已过期，请重新登录", code=4010, trace_id=self._trace_id)
        if resp.status_code >= 500:
            raise SkyApiError(
                "后端服务异常，请稍后重试", code="UPSTREAM_ERROR", retryable=True, trace_id=self._trace_id
            )
        try:
            body = resp.json()
        except ValueError as exc:
            raise SkyApiError(
                "后端返回格式异常", code="INVALID_RESPONSE", retryable=True, trace_id=self._trace_id
            ) from exc
        # 老吴外卖统一返回结构：{code:1 成功, 其他失败, msg}
        if body.get("code") != 1:
            raise SkyApiError(
                body.get("msg") or "未知错误", code=body.get("code") or 0, trace_id=self._trace_id
            )
        return body.get("data")

    async def get(self, path: str, params: dict | None = None):
        return await self._request("GET", path, params=params)

    async def post(self, path: str, json: dict | None = None):
        return await self._request("POST", path, json=json or {})

    async def put(self, path: str, json: dict | None = None):
        return await self._request("PUT", path, json=json or {})
