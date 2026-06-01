# Local Dependency Baseline

`infra/` 目录用于维护 SuperAgent 本地依赖说明和后续环境脚本。阶段 0 不生成 Docker Compose，只固定连接约定，避免与现有 Docker 服务冲突。

## 当前接入策略

- 优先复用当前机器已运行的 Docker 服务。
- PostgreSQL/pgvector、Redis、MinIO 为当前必需依赖。
- Kafka 为文档异步处理预留，阶段 0 只保留连接变量，默认 `KAFKA_ENABLED=false`。
- Neo4j、MySQL 不纳入当前阶段实现范围。

## 本地依赖映射

| 依赖 | 本地地址 | 用途 | 备注 |
| --- | --- | --- | --- |
| PostgreSQL/pgvector | `localhost:5432` | 主库、pgvector、全文检索 | 容器名 `PostgreSQL` |
| Redis | `localhost:6379` | 缓存、运行锁、租约 | 容器名 `redis` |
| MinIO API | `http://localhost:9000` | 文档对象存储 | 容器名 `minio` |
| MinIO Console | `http://localhost:9001` | Bucket 管理 | 容器名 `minio` |
| Kafka | `localhost:9092` | 文档异步任务 | 当前可选，未启动时保持禁用 |

## 凭据确认方式

PostgreSQL 用户名、密码和 MinIO Access Key/Secret Key 以当前容器环境变量为准。

```bash
docker inspect PostgreSQL --format '{{range .Config.Env}}{{println .}}{{end}}'
docker inspect minio --format '{{range .Config.Env}}{{println .}}{{end}}'
```

## 环境变量落点

- 后端模板：`../backend/.env.example`
- 前端模板：`../frontend/.env.example`

关键变量和连接关系：

- `POSTGRES_URL` 指向 `jdbc:postgresql://localhost:5432/superagent`
- `REDIS_URL` 指向 `redis://localhost:6379`
- `MINIO_ENDPOINT` 指向 `http://localhost:9000`
- `KAFKA_BOOTSTRAP_SERVERS` 指向 `localhost:9092`
- `VITE_API_BASE_URL` 指向后端未来的 `http://localhost:8080/api/v1`

## 后续开发启动顺序

1. 确认 Docker 中 PostgreSQL、Redis、MinIO 可用。
2. 创建 `superagent` 数据库，并启用 `vector`、`pg_trgm` 扩展。
3. 复制 `backend/.env.example` 和 `frontend/.env.example` 为本地环境文件。
4. 阶段 1 初始化后端工程并验证数据库连通性。
5. 阶段 3 初始化前端工程并验证能访问后端 API。
