# 老吴外卖：全链路外卖系统 + AI 客服 Agent

这是一个可运行的外卖业务项目，包含用户端小程序、商家管理后台、Spring Boot 后端，以及基于 LangGraph 的 AI 客服 Agent。项目重点不是“接入一个聊天窗口”，而是展示 AI 如何安全地调用真实业务接口。

## 项目亮点

- **真实业务闭环**：菜品、购物车、下单、支付 Mock、订单状态流转、配送、催单、退款和售后。
- **Agent 工程化**：LangGraph 状态图、SQLite 会话持久化、流式 SSE、工具调用、Trace ID、超时和结构化错误。
- **安全边界**：用户/管理员 JWT 校验、会话归属校验、订单归属由后端确认、高风险操作二次确认、批量副作用调用隔离。
- **可观测与可维护**：健康检查、就绪检查、会话审计、FAQ 原子更新、推荐过滤、只读经营报表 Agent。
- **可验证交付**：Agent、Java 和管理端均有独立测试或构建命令，当前基线为 Agent `40 passed`、Java `mvn test` 通过、Vue 生产构建通过。

## 架构

```text
微信小程序 / Vue 管理后台
             │
             ▼
      Spring Boot API (8080)
       ├── MySQL
       └── Redis
             ▲
             │ 用户 JWT / 管理员 JWT + X-Trace-Id
      FastAPI + LangGraph Agent (8000)
       ├── 只读查询工具
       ├── 高风险操作确认节点
       ├── FAQ 检索与管理
       └── 管理员只读报表工具
```

## 目录

| 目录 | 内容 |
| --- | --- |
| `sky-take-out/` | Spring Boot 多模块后端、数据库脚本、Agent 服务和 Docker Compose |
| `sky-web/` | Vue 2 + TypeScript 商家管理后台 |
| `sky-miniapp/` | uni-app 微信小程序用户端 |
| `sky-take-out/docs/` | 架构、部署、验收和后续改进文档 |

## 本地运行

### 1. 后端依赖

在 `sky-take-out/` 下准备 Docker Desktop，然后执行：

```powershell
docker compose up -d mysql redis
```

也可以使用 IDEA 启动 `sky-server`，默认端口 `8080`。

### 2. Agent

```powershell
cd sky-take-out/sky-agent
Copy-Item .env.example .env
# 编辑 .env，填入本地 DEEPSEEK_API_KEY 和 JWT 配置
.venv\Scripts\python.exe -m uvicorn app.main:app --port 8000
```

### 3. 管理后台

```powershell
cd sky-web
npm ci
$env:NODE_OPTIONS="--openssl-legacy-provider"
npm run serve
```

打开 `http://localhost:8888`。小程序使用 HBuilderX 导入 `sky-miniapp/` 后运行到微信开发者工具。

## 验证命令

```powershell
# Agent
cd sky-take-out/sky-agent
.venv\Scripts\python.exe -m pytest -q

# Java
cd ..
mvn test -q

# 管理后台
cd ..\..\sky-web
npm run build
```

## Agent 示例

- 用户：“我的订单到哪了” -> 查询本人订单或订单时间线。
- 用户：“帮我取消订单” -> 先返回确认节点，拒绝时不执行写操作。
- 用户：“有什么招牌菜” -> 价格和库存来自后端实际菜品接口。
- 管理员查询报表 -> `/admin/report-agent/query`，仅允许固定只读指标和有限日期范围。

## 安全设计

- 用户与管理员使用独立的 JWT 体系，接口按角色分别校验。
- 会话与订单的归属由后端确认，Agent 无法越权访问他人数据。
- 取消订单、退款等高风险操作必须经过确认节点，用户拒绝时不会执行写操作。
- 内部服务接口（`/internal/**`）独立鉴权；示例配置中的密钥均为占位符，真实凭证通过环境变量注入，不入库。

## 后续方向

- checkpoint 存储从 SQLite 演进到 PostgreSQL/Redis 多实例，支撑横向扩展。
- 引入生产级监控告警与灰度发布流程。
- Agent 能力扩展：更多业务工具、更细粒度的权限控制。

完整的启动步骤与全链路验收记录见 [完整启动与全链路验收](sky-take-out/docs/完整启动与全链路验收.md)。
