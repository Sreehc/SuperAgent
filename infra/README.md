# Local Dependencies

`infra/` 维护本地依赖接入约定和后续脚本入口。当前仓库默认复用本机已运行的 Docker 服务，不额外强制生成 Compose。

## 依赖映射

| 依赖 | 地址 | 用途 |
| --- | --- | --- |
| PostgreSQL/pgvector | `localhost:5432` | 主库、向量检索、全文检索 |
| Redis | `localhost:6379` | 缓存、运行锁、租约预留 |
| MinIO API | `http://localhost:9000` | 原始文档存储 |
| MinIO Console | `http://localhost:9001` | Bucket 管理 |
| Kafka | `localhost:9092` | 文档异步任务，可选 |

## 启动顺序

1. 确认 PostgreSQL/pgvector 与 MinIO 可用
2. 创建数据库 `superagent`
3. 启用扩展 `vector`、`pg_trgm`
4. 复制 `backend/.env.example` 和 `frontend/.env.example`
5. 启动后端，再启动前端

## 关键说明

- 默认 `KAFKA_ENABLED=false`，适合不跑异步文档消费的本地开发。
- 如果要完整验证文档自动解析链路，需要自行准备 Kafka。
- 凭据请通过容器环境变量确认，不要把真实密钥写回仓库。

## GitHub Actions 自动部署

仓库提供 `.github/workflows/deploy.yml`，用于在 `main` 分支推送或手动触发时：

1. 在 GitHub Actions 中构建后端、Agent Service 和前端。
2. 打包 `backend/app.jar`、`agent-service/app.jar`、`frontend/dist`、`sandbox-runner` 和 `plugins`。
3. 通过 SSH 上传到服务器。
4. 在服务器上执行 `infra/scripts/deploy-release.sh`，切换 `/opt/superagent/current` 并重启已有 systemd 服务。

部署脚本不会创建数据库、Redis、MinIO、Kafka、Nginx 等基础设施，默认复用服务器已经存在的环境。当前 systemd 模板使用服务器已有 Docker 运行 Java 21 镜像，适合服务器本机 JDK 不是 21 的情况。

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

### 服务器准备

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

如果还没有 Nginx 配置，可以以 `infra/nginx/superagent.conf` 为模板，替换 `server_name` 和路径后启用。默认前端静态文件路径为：

```text
/opt/superagent/current/frontend
```
