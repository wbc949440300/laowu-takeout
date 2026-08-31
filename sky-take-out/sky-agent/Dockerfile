# 老吴外卖智能客服 Agent 镜像
FROM python:3.13-slim

WORKDIR /app

# 先装依赖（利用层缓存）
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app app
COPY knowledge knowledge

# 运行时配置通过环境变量注入（DEEPSEEK_API_KEY 等，见 docker-compose）
EXPOSE 8000

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
