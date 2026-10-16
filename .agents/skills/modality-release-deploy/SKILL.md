---
name: modality-release-deploy
description: 在 modality 构建镜像、compose 部署、初始化管理员或验证部署时使用，覆盖构建、启动、seed-admin、冒烟验证、生产口径核对与回滚。
---

# 发布与部署

## 构建镜像

```sh
docker build -t registry.example.com/modality:<tag> .
```

- Dockerfile 多阶段：builder 跑 `:modality-boot:bootJar -x test`——**测试必须在构建前本地跑过**，镜像里不做验证
- 运行时 `eclipse-temurin:21-jre`，非 root 用户，`TZ=Asia/Shanghai`

## compose 一键（应用 + MySQL + Redis）

```sh
cp .env.example .env    # 至少改 MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD / MODALITY_REDIS_PASSWORD
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

- 首次启动自动建表（幂等）；无内置账号
- 播种管理员（等应用启动、建表结束后执行）：

```sh
set -a && . ./.env && set +a
docker compose exec -T mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < db/seed-admin.sql
```

- `admin / Admin@12345` 带 `must_change_password`，登录后第一件事 `POST /auth/password/change` 改密

## 冒烟验证

```sh
curl -s http://localhost:8080/actuator/health    # {"status":"UP"}
# 再走一遍：取验证码 → 登录 → 带 satoken 调一个真实端点
```

## 生产口径核对（上线前）

- 单租户：`modality.tenant.status-check.missing-tenant-policy=ALLOW`；多租户保持 `REJECT`
- CORS 配实际域名；Cookie `Secure` 要求 HTTPS（Nginx 终止 TLS 时透传 `X-Forwarded-*`）
- 建议 `modality.jwt.fail-on-missing-key=true`，密钥轮换就位
- 暴露面：management 端点最小集、`modality.web.open-api.enabled=false`
- 备份：MySQL `mysqldump` + Redis `appendonly.aof`（吊销名单丢失会导致已吊销令牌复活）

## 回滚

- 镜像按 tag 回滚；涉及表结构变更的版本先看对应升级说明（见 [modality-upgrade-notes](../modality-upgrade-notes/SKILL.md)）
- 新表无需回滚；变更列按升级说明的反向 DDL 处理

## 纪律

- 宣布「部署成功」前必须完成冒烟；compose 命令成功 ≠ 应用健康
- 生产不用 dev profile；`.env` 不入库
