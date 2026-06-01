# Backend Scaffold

当前目录已完成阶段 1 的 Spring Boot 基线初始化，统一使用 Java 21 目标版本。

## 已完成内容

- Spring Boot + Maven 工程和 Maven Wrapper。
- 模块化单体包结构：`api`、`auth`、`chat`、`rag`、`knowledge`、`observability`、`infra`、`common`。
- 统一响应和统一异常处理。
- 基础系统接口：`/api/v1/system/bootstrap`、`/api/v1/system/modules/{module}`。
- Actuator 健康检查：`/actuator/health`。
- OpenAPI/Swagger 基础配置：`/v3/api-docs`、`/swagger-ui.html`。
- Flyway 迁移框架，以及 PostgreSQL 扩展、基础表结构、索引和 `updated_at` 触发器迁移。

## 启动前提

- JDK 21
- 本地 PostgreSQL 容器可用
- 数据库存在：`superagent`

如果本机 Maven 默认不使用 Java 21，启动和测试时显式指定：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## 本地启动

当前 PostgreSQL 容器实际密码需要以容器环境变量为准。当前机器可通过：

```bash
docker inspect PostgreSQL --format '{{range .Config.Env}}{{println .}}{{end}}'
```

示例启动命令：

```bash
POSTGRES_USER=postgres \
POSTGRES_PASSWORD=root \
POSTGRES_URL=jdbc:postgresql://localhost:5432/superagent \
./mvnw spring-boot:run
```

## 验证命令

```bash
TEST_POSTGRES_PASSWORD=root ./mvnw test
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/system/bootstrap
```

## 范围边界

当前阶段只完成基础工程、配置和迁移，不包含：

- 认证逻辑
- 会话逻辑
- RAG 业务逻辑
- 文档处理业务逻辑

## 下一阶段入口

阶段 2 从认证、多租户上下文、权限拦截和相关数据访问层开始。
