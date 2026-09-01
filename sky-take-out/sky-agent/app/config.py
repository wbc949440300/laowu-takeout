"""全局配置：从 .env / 环境变量读取"""
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    # DeepSeek（OpenAI 兼容）
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-v4-flash"

    # Java 后端
    sky_server_base_url: str = "http://localhost:8080"
    sky_http_connect_timeout: float = 3.0
    sky_http_read_timeout: float = 15.0
    sky_http_max_connections: int = 50
    sky_http_max_keepalive_connections: int = 20

    # 用户端 JWT 验签
    user_jwt_secret: str = "dev-user-jwt-secret-change-before-production-2026"
    admin_jwt_secret: str = "dev-admin-jwt-secret-change-before-production-2026"
    jwt_issuer: str = "sky-take-out"
    jwt_user_audience: str = "sky-user"
    jwt_admin_audience: str = "sky-admin"
    agent_environment: str = "dev"

    # 逗号分隔的跨域白名单。开发环境可使用 *，生产环境必须配置明确域名。
    cors_origins: str = "http://localhost:8081,http://127.0.0.1:8081,http://localhost:8091,http://127.0.0.1:8091,http://localhost:8888,http://127.0.0.1:8888"

    agent_port: int = 8000
    max_input_chars: int = 4000
    max_history_messages: int = 30
    max_output_tokens: int = 1024
    session_ttl_seconds: int = 7 * 24 * 3600
    max_sessions_per_user: int = 20

    def parsed_cors_origins(self) -> list[str]:
        origins = [origin.strip() for origin in self.cors_origins.split(",")]
        return [origin for origin in origins if origin]

    def validate_runtime(self) -> None:
        if self.agent_environment.lower() != "prod":
            return
        if len(self.deepseek_api_key) < 32 or self.deepseek_api_key.startswith("your-"):
            raise RuntimeError("生产环境必须配置有效的 DEEPSEEK_API_KEY")
        if len(self.user_jwt_secret) < 32 or self.user_jwt_secret.startswith("dev-"):
            raise RuntimeError("生产环境 USER_JWT_SECRET 长度必须至少为 32 个字符")
        if not self.jwt_issuer or not self.jwt_user_audience:
            raise RuntimeError("生产环境必须配置 JWT_ISSUER 和 JWT_USER_AUDIENCE")
        if "*" in self.parsed_cors_origins():
            raise RuntimeError("生产环境 CORS_ORIGINS 不允许使用 *")


@lru_cache
def get_settings() -> Settings:
    return Settings()
