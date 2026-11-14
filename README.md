# Austin 多租户消息推送平台

Austin 是一个面向企业通知、业务触达和个人开发者场景的统一消息推送平台，将站内信、短信、邮件、Push、微信、企业微信、钉钉、飞书、支付宝小程序和通用 Webhook 等 13 类渠道收敛到统一的模板、发送、调度、消费、追踪和治理链路中。

在 Austin 原有消息平台能力之上，本项目进一步实现了：

- **多租户与权限**：租户隔离、用户/角色/权限体系
- **配额计费**：额度预占-结算-释放三阶段模型、Outbox 可靠事件、沙箱支付（Mock / Stripe / 支付宝）
- **可靠投递**：RabbitMQ Publisher Confirm、消费端重试、MQ Inbox 幂等消费与死信重放
- **接入治理**：责任链编排去重、夜间屏蔽、敏感词、渠道级限流
- **运营总览**：基于 Redis 分桶计数的实时发送量、成功率与渠道健康度大盘

## 仓库结构

```
Austin/
├── backend/    # Java 后端（Spring Boot 2.5 + MySQL + Redis + RabbitMQ/Kafka + Flink）
│   ├── austin-web/             # Web 入口（API、运营总览聚合接口）
│   ├── austin-auth/            # 认证与权限
│   ├── austin-billing/         # 配额计费与支付
│   ├── austin-handler/         # 消费侧责任链（去重/屏蔽/渠道发送）
│   ├── austin-service-api-impl/# 发送侧责任链（预检/配额/组装/投递）
│   ├── austin-support/         # 公共支撑（DAO、Redis、MQ 适配层）
│   ├── austin-cron/            # 定时任务（xxl-job）
│   ├── austin-stream/          # Flink 埋点流处理
│   └── austin-data-house/      # Hive 离线数仓
└── frontend/   # React/Vue 管理台（Vue3 + Ant Design Vue + Vite）
```

## 快速开始

### 后端

1. 准备中间件（可用仓库内 docker-compose 一键拉起）：

```bash
cd backend
docker-compose -f docker-compose-austin-all.yml up -d
```

2. 创建本地环境配置（已被 git 忽略，不会提交）：

```bash
cd austin-web/src/main/resources
cp application-dev.properties.example application-dev.properties
# 编辑 application-dev.properties，填入你的 MySQL/Redis 密码与支付签名密钥
```

3. 启动（JDK 8+，Maven 3.6+）：

```bash
mvn spring-boot:run -pl austin-web
# 服务默认端口 7778，接口文档 /doc.html
```

### 前端

```bash
cd frontend
npm install
npm run dev      # 开发模式，默认 http://localhost:3000
npm run build    # 产物输出 dist/
```

## 配置说明

| 配置文件 | 说明 |
|---|---|
| `application.properties` | 主配置，敏感项均通过 `${ENV_VAR:}` 占位符注入，默认值留空 |
| `application-dev.properties.example` | 本地开发配置示例，复制为 `application-dev.properties` 后填入真实值 |
| `docker-compose*.yml` | 本地中间件编排，数据库密码为演示默认值 `123456`，部署时请修改 |

**注意**：`application-dev.properties` / `application-mac.properties` 含环境密码，已在 `.gitignore` 中排除，请勿提交。

## 开源致谢

本项目基于开源项目 [Austin](https://github.com/ZhongFuCheng3y/austin)（Apache License 2.0）二次开发，感谢原作者 [ZhongFuCheng3y](https://github.com/ZhongFuCheng3y) 的开源贡献。
