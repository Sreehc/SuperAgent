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
