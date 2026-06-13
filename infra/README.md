# Infra and Deployment

`infra/` 维护 SuperAgent 的本地依赖接入约定、服务器部署模板、systemd 服务模板、Nginx 配置和发布脚本。当前仓库默认复用本机或服务器已运行的基础设施，不额外强制生成 Docker Compose。

## 依赖映射

| 依赖 / 服务 | 默认地址 | 用途 |
| --- | --- | --- |
| PostgreSQL/pgvector | `localhost:5432` | 主库、Flyway 迁移、向量检索、全文检索 |
| Redis | `localhost:6379` | 缓存、运行锁、停止信号、租约预留 |
| MinIO API | `http://localhost:9000` | 原始文档存储 |
| MinIO Console | `http://localhost:9001` | Bucket 管理 |
| Kafka | `localhost:9092` | 文档异步任务，可选 |
| Neo4j Bolt | `bolt://localhost:7687` | 文档图谱与 `graph.query` 工具，可选 |
| Neo4j Browser | `http://localhost:7474` | 图谱调试，可选 |
| Backend API | `http://localhost:8080` | `/api/v1`、Swagger、Actuator |
| Agent Service | `http://localhost:18081` | Agent Run、工具执行、stream/resume/cancel，可选 |
| Sandbox Runner | `http://localhost:18082` | 本地直接运行的 Python sandbox，可选 |
| Sandbox Runner(systemd) | `http://127.0.0.1:18122` | 服务器 systemd 模板端口 |
| Frontend dev server | `http://localhost:5173` | Vite 本地开发 |
| Nginx static root | `/opt/superagent/current/frontend` | 生产前端静态文件 |
| Core plugin manifest | `plugins/core-tools/manifest.json` | `knowledge.search`、`web.search`、`web.fetch`、`http.request`、`graph.query`、`python.sandbox` |

## 本地启动顺序

1. 确认 PostgreSQL/pgvector 可用。
2. 创建数据库 `superagent` 或测试库 `superagent_test`。
3. 启用扩展 `vector`、`pg_trgm`。
4. 确认 MinIO 可用，并准备 `superagent-documents` bucket 或启用自动创建。
5. 确认 Redis 可用。
6. Kafka 可选；生产异步文档链路建议启用。
7. Neo4j 可选；图谱能力和 graph tool 需要启用。
8. 复制或参考 `backend/.env.example` 和 `frontend/.env.example`。
9. 启动 Backend API。
10. 按需启动 Agent Service。
11. 按需启动 Sandbox Runner。
12. 启动 Frontend dev server 或 Nginx。

## 关键说明

- 默认 `KAFKA_ENABLED=false`，适合不跑异步文档消费的本地开发。
- 如果要完整验证生产式文档自动解析链路，需要自行准备 Kafka。
- 如果要在关闭 Kafka 时完成本地文档处理闭环，可设置 `INLINE_DOCUMENT_PROCESSING_WHEN_KAFKA_DISABLED=true`。
- 图谱能力默认受 `GRAPH_ENABLED=false` 控制；启用前需要可用 Neo4j。
- Web Search、HTTP Tool、Graph Tool、Python Sandbox 等外部/高风险能力默认应通过环境变量、运行时设置和租户工具绑定显式开启。
- 仓库默认不提供强制 Compose；部署脚本不会创建数据库、Redis、MinIO、Kafka、Neo4j、Nginx 等基础设施。
- 真实密钥只放服务器 env 文件或 GitHub Secrets，不写回仓库。

## GitHub Actions

仓库当前包含三类 workflow：

| Workflow | 用途 |
| --- | --- |
| `.github/workflows/deploy.yml` | 构建 Backend、Agent Service、Frontend，打包 Sandbox Runner 和 Plugins，并通过 SSH 发布到服务器 |
| `.github/workflows/rag-eval.yml` | 启动 pgvector PostgreSQL 和 MinIO，运行 RAG 评测脚本并上传 summary artifact |
| `.github/workflows/agent-eval.yml` | 启动 pgvector PostgreSQL、MinIO 和 Neo4j，运行 Agent 评测脚本并上传 summary artifact |

评测脚本入口：

```bash
python3 scripts/rag_eval.py
python3 scripts/agent_eval.py
```

评测脚本会写入 `artifacts/rag-eval-summary.json` 或 `artifacts/agent-eval-summary.json`。

## GitHub Actions 自动部署

仓库提供 `.github/workflows/deploy.yml`，用于在 `main` 分支推送或手动触发时：

1. 在 GitHub Actions 中构建 Backend API、Agent Service 和 Frontend。
2. 打包 release archive，包含：
   - `backend/app.jar`
   - `agent-service/app.jar`
   - `frontend/`
   - `sandbox-runner/`
   - `plugins/`
3. 通过 SSH 上传到服务器。
4. 在服务器上执行 `infra/scripts/deploy-release.sh`。
5. 切换 `/opt/superagent/current` 并重启已有 systemd 服务。
6. 按配置执行健康检查并清理旧 release。

部署脚本只发布应用产物和重启服务，不创建外部基础设施，也不生成生产 env 文件。

### GitHub Secrets

在 GitHub 仓库的 `Settings -> Secrets and variables -> Actions` 中配置：

| 名称 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `DEPLOY_HOST` | 是 | `203.0.113.10` | 服务器 IP 或域名 |
| `DEPLOY_USER` | 是 | `deploy` | SSH 登录用户 |
| `DEPLOY_SSH_KEY` | 是 | 私钥内容 | 服务器已授权的私钥 |
| `DEPLOY_PATH` | 是 | `/opt/superagent` | 服务器部署目录 |
| `DEPLOY_SSH_PORT` | 否 | `22` | SSH 端口 |
| `DEPLOY_KNOWN_HOSTS` | 否 | `ssh-keyscan` 输出 | 固定 host key，留空时工作流会自动 `ssh-keyscan` |

建议同时配置 GitHub Variables：

| 名称 | 默认值 | 说明 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api/v1` | 前端构建时写入的 API 地址 |
| `VITE_SSE_TIMEOUT_SECONDS` | `120` | 前端 SSE 超时时间 |
| `VITE_APP_NAME` | `SuperAgent` | 浏览器标题 |
| `DEPLOY_BACKEND_SERVICE` | `superagent-backend` | 后端 systemd 服务名，填 `skip` 可跳过 |
| `DEPLOY_AGENT_SERVICE` | `superagent-agent-service` | Agent Service systemd 服务名，填 `skip` 可跳过 |
| `DEPLOY_SANDBOX_SERVICE` | `superagent-sandbox-runner` | Sandbox Runner systemd 服务名，填 `skip` 可跳过 |
| `DEPLOY_KEEP_RELEASES` | `5` | 服务器保留的历史版本数 |
| `DEPLOY_SUDO` | `sudo -n` | 重启服务使用的提权命令；root 用户可设为 `none` |
| `DEPLOY_INSTALL_SANDBOX_DEPS` | `false` | 是否用服务器已有 `python3` 更新 sandbox venv；需要时可设为 `auto` |
| `DEPLOY_HEALTHCHECK_URLS` | `http://127.0.0.1:18121/actuator/health` | 部署后健康检查 URL，多个 URL 用空格分隔；设为 `skip` 可跳过 |

## 服务器准备

如果服务器已经有服务和 Nginx，只需要让它们指向部署目录：

```bash
sudo mkdir -p /opt/superagent/shared/env
sudo chown -R deploy:deploy /opt/superagent
```

生产环境变量放在服务器本地，不进 Git：

```text
/opt/superagent/shared/env/backend.env
/opt/superagent/shared/env/agent-service.env
/opt/superagent/shared/env/sandbox-runner.env
```

如果还没有 systemd 服务，可以参考：

```bash
sudo cp infra/systemd/superagent-*.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable superagent-backend superagent-agent-service superagent-sandbox-runner
```

### systemd 模板说明

- `superagent-backend.service`
  - 使用 `eclipse-temurin:21-jre` Docker 镜像。
  - 挂载 `/opt/superagent/current/backend/app.jar`。
  - 读取 `/opt/superagent/shared/env/backend.env`。
  - 使用 host network。
- `superagent-agent-service.service`
  - 使用 `eclipse-temurin:21-jre` Docker 镜像。
  - 挂载 `/opt/superagent/current/agent-service/app.jar`。
  - 挂载 `/opt/superagent/current/plugins`。
  - 读取 `/opt/superagent/shared/env/agent-service.env`。
  - 使用 host network。
- `superagent-sandbox-runner.service`
  - 以 `superagent:superagent` 运行。
  - 使用 `/opt/superagent/shared/sandbox-venv` 中的 Uvicorn。
  - 监听 `127.0.0.1:18122`。
  - 读取 `/opt/superagent/shared/env/sandbox-runner.env`。

所有模板都启用 `NoNewPrivileges=true`；sandbox runner 额外使用 `PrivateTmp=true`。

## Nginx

如果还没有 Nginx 配置，可以以 `infra/nginx/superagent.conf` 为模板，替换 `server_name` 和路径后启用。

模板默认：

- `server_name superagent.wandcheers.xyz`
- 监听 `80`
- 前端静态文件路径：`/opt/superagent/current/frontend`
- SPA fallback：`try_files $uri $uri/ /index.html`
- `/api/` 代理到 `http://127.0.0.1:18121`
- 关闭 proxy buffering，并设置较长读写超时以兼容 SSE streaming
- `/actuator/` 返回 `404`

## 健康检查

已存在的健康检查入口：

```bash
curl http://127.0.0.1:18121/actuator/health
curl http://127.0.0.1:18122/health
```

Agent Service 当前不在 README 中承诺独立 health endpoint；以 systemd 状态、Backend 调用链路和 Agent/Tools 功能验证为准。
