# Austin 组件说明与部署手册

> 本文面向「用 Docker Desktop 部署 Austin」的场景，说明每个组件的功能、需要开启哪些组件，以及对应的启动命令。

## 1. 组件清单

Austin 的 `docker-compose-austin-all.yml` 定义了 21 个服务，分为**核心**与**可选增强**两类。

### 1.1 核心组件（跑通项目必须）

| 服务名 | 镜像 | 功能 |
|--------|------|------|
| `austin-mysql` | mysql:5.7 | 一切持久化：消息模板、渠道账号、发送记录、用户/登录、审计 |
| `austin-redis` | redis:3.2 | 登录 Session、消息去重、流量控制、发送全链路日志追踪 |
| `rabbitmq` | rabbitmq:3.9-management | 消息队列：解耦「发送入口」与「各渠道 Handler 消费」，异步/削峰 |
| `austin-nacos` | nacos-server:v2.1.0 | 配置中心：动态推送过滤/去重/流控/限流规则，hades 规则引擎依赖 |
| `austin-xxl-job` | xxl-job-admin:2.3.0 | 定时任务调度：csv 万人群发、夜间屏蔽、周期刷新 access_token |
| `austin` | austin:1.0 | 主业务应用（austin-web），聚合上述能力提供全部接口 |

### 1.2 可选增强组件

| 服务名 | 镜像 | 功能 | 对应代码 |
|--------|------|------|----------|
| `prometheus` | prom/prometheus | 指标采集 | micrometer + actuator |
| `alertmanager` | prom/alertmanager | 监控告警 | 告警通知 |
| `grafana` | grafana/grafana | 监控大盘可视化 | 监控展示 |
| `node-exporter` | quay.io/prometheus/node-exporter | 机器指标采集 | 监控数据源 |
| `cadvisor` | google/cadvisor | 容器指标采集 | 监控数据源 |
| `mongo` | mongo:4.2 | Graylog 存储 | 日志 |
| `elasticsearch` | elasticsearch-oss:7.10.2 | Graylog 索引检索 | 日志 |
| `graylog` | graylog/graylog:4.2 | 分布式日志收集展示 | logback-gelf |
| `jobmanager` / `taskmanager` | flink:1.16.1 | 实时消费 austinTraceLog | austin-stream |
| `namenode` / `datanode` | bde2020/hadoop-* | HDFS 存储 | austin-data-house |
| `hive-metastore` / `hive-server` / `hive-metastore-postgresql` | bde2020/hive:* | Hive 数据仓库 | austin-data-house |
| `presto-coordinator` | shawnzhu/prestodb | 即席查询 | austin-data-house |
| `metabase` | metabase/metabase | 报表图表可视化 | 数据分析 |

## 2. 功能 → 需要开启的组件

| 你要的功能 | 必需组件 | 说明 |
|-----------|----------|------|
| 项目能访问 + 登录 | MySQL + Redis + Austin | 最底线 |
| 能发消息（IM/邮箱/飞书/Webhook/短信） | 上面 + RabbitMQ | MQ 消费链路需要 |
| 发送去重/流控/动态规则 | 上面 + Nacos | hades 规则引擎依赖 |
| 定时/周期/人群群发 | 上面 + XXL-JOB | cron 调度 |
| 监控大盘 | + Prometheus/Grafana/Alertmanager/Exporter/cAdvisor | |
| 集中检索日志 | + Graylog(ES/Mongo) | |
| 消息实时链路处理 | + Flink | 另需提交 austin-stream jar |
| 离线报表/数据仓库 | + Hive/Hadoop/Presto + Metabase | austin-data-house，需单独构建 |

> 注意：Flink、Hive 这类除了起容器，还必须**单独构建并提交 `austin-stream` / `austin-data-house` 的 jar**。目前的 `austin-data-house` 因旧版 hive/calcite → pentaho（conjars 仓库已下线）依赖，阿里云镜像拉不到，构建会失败。绝大多数场景用不到，**建议日常只跑核心 6 个**。

## 3. 部署命令

### 3.1 一次性启动全部（首次拉镜像，较慢；适合一次性初始化和调试）

```powershell
# 1) 并入渠道迁移 SQL（首次建库自动执行）
#    拷贝后必须重命名为 zz_ 前缀，确保排在 austin.sql 之后执行
#    （否则 USE austin 时库未建，会中断初始化，见 6.1）
Copy-Item "doc/sql/migrations/V20260820__personal_developer_channels.sql" "doc/sql/"
Rename-Item "doc/sql/V20260820__personal_developer_channels.sql" "zz_V20260820__personal_developer_channels.sql"

# 2) 构建主应用镜像（只需主应用 jar；不要整包 mvn package，会踩 data-house 的死链）
mvn -pl austin-web -am -DskipTests package
docker compose -f docker-compose-austin-all.yml build austin

# 3) 校验并启动
docker compose -f docker-compose-austin-all.yml config -q
docker compose -f docker-compose-austin-all.yml up -d
```

### 3.2 日常启动核心（推荐）

```powershell
docker compose -f docker-compose-austin-all.yml up -d austin-mysql austin-redis rabbitmq austin-nacos austin-xxl-job austin
```

### 3.3 按需启用可选能力

```powershell
# 监控
docker compose -f docker-compose-austin-all.yml up -d prometheus alertmanager grafana node-exporter cadvisor

# 集中日志
docker compose -f docker-compose-austin-all.yml up -d mongo elasticsearch graylog

# 实时计算
docker compose -f docker-compose-austin-all.yml up -d jobmanager taskmanager

# 数据仓库
docker compose -f docker-compose-austin-all.yml up -d namenode datanode hive-metastore hive-server hive-metastore-postgresql presto-coordinator

# 数据可视化
docker compose -f docker-compose-austin-all.yml up -d metabase
```

### 3.4 常用管理命令

```powershell
# 查看运行状态
docker compose -f docker-compose-austin-all.yml ps

# 查看某个组件日志
docker compose -f docker-compose-austin-all.yml logs -f <服务名>

# 重启某个组件
docker compose -f docker-compose-austin-all.yml restart <服务名>

# 停止全部
docker compose -f docker-compose-austin-all.yml down

# 停止并删除数据卷（谨慎，会清空数据库）
docker compose -f docker-compose-austin-all.yml down -v
```

## 4. 访问入口

| 组件 | 地址 | 账号 |
|------|------|------|
| Austin 主应用 | http://localhost:7777 | dev-user / admin123 |
| RabbitMQ | http://localhost:15672 | root / 123456 |
| Nacos | http://localhost:8848/nacos | nacos / nacos |
| XXL-JOB | http://localhost:18080/xxl-job-admin | admin / 123456 |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| Graylog | http://localhost:9009 | admin / admin |
| Flink | http://localhost:18081 | - |
| Hadoop NameNode | http://localhost:50070 | - |
| HiveServer2 | jdbc:hive2://localhost:10000 | - |
| Metabase | http://localhost:5001 | 首次访问设置 |

## 5. 注意事项

1. **消息队列**：应用通过 `PARAMS` 的 `--austin.mq.pipeline=rabbitMq --austin.rabbitmq.ip=rabbitmq --austin.rabbitmq.port=5672` 使用 RabbitMQ。若复用你已有的 RabbitMQ，把 `rabbitmq` 服务注释掉，并将 ip 改为实际地址（账号需与 `spring.rabbitmq` 一致：root / 123456，vhost `/`）。
2. **首次建库**：`doc/sql/` 下每个 `.sql` 自带 `CREATE DATABASE`，会自动建出 `austin` / `xxl_job` / `nacos_config` 三个库；渠道迁移脚本需先复制进该目录并**重命名**，确保它排在 `austin.sql` 之后执行（见 3.1 第 1 步）。
3. **Nacos 配置**：`austin`（test profile）默认开启 hades，启动时会去 Nacos 拉取 `dataId=austin`、group `DEFAULT_GROUP` 的配置。若 Nacos 无该配置可能启动失败，需在 Nacos 新建该配置，或临时将 `austin.rule.engine.enabled` 改为 `false`。
4. **内存**：全量含 ES+Mongo+Hadoop+Flink 需 8G+ 内存。日常核心 6 个约 2~3G 即可。
5. **端口冲突**：Windows 上若 3306/6379/8080/3000 已被占用，改映射或停本地服务。本机有原生 MySQL 时，compose 的 MySQL 宿主端口已改为 `33306`（容器间仍走 `austin-mysql:3306`）。Hive 的 PostgreSQL 已偏移到 `5433`。

## 6. 常见问题排查

### 6.1 初始化脚本顺序导致建库失败（Nacos 报 No DataSource set）
`/docker-entrypoint-initdb.d` 下的脚本**按文件名排序执行**。
- 症状：MySQL 里没有 `austin`/`xxl_job`/`nacos_config`，Nacos 启动报 `Nacos Server did not start ... No DataSource set`，Austin 因此也起不来、页面无法访问。
- 根因：渠道迁移脚本 `V20260820__personal_developer_channels.sql`（`V` 开头）排序在最前，先于 `austin.sql` 执行，而它内部 `USE austin` 此时库还未创建，报 `Unknown database 'austin'` 后中断后续初始化。
- 修复：把迁移脚本重命名为 `zz_V20260820__...sql`，让它排在 `austin.sql`(\`nacos.sql\`\`xxl-job.sql\`) 之后。
- 注意：一旦初始化过，再改名不会生效，需**删除 MySQL 数据卷重建**：
  ```powershell
  docker compose -f docker-compose-austin-all.yml rm -sf austin-mysql
  docker volume rm austin-all_mysql-data
  docker compose -f docker-compose-austin-all.yml up -d austin-mysql
  ```

### 6.2 容器名冲突（`/rabbitmq` already in use）
本地已有同名容器时，先改名并停掉旧实例再启动：
```powershell
docker rename <旧名> austin-rabbitmq-old
docker stop austin-rabbitmq-old
docker compose -f docker-compose-austin-all.yml up -d rabbitmq
```

### 6.3 端口被占（3306/8080/3000）
- 3306：本机原生 MySQL 占用时，已把映射改为 `33306:3306`。
- 其它端口同理：改 compose 的 `ports` 映射，或停掉占用进程。

### 6.4 Austin 访问返回 401
`http://localhost:7777` 返回 `401` 表示服务已正常启动，只是未登录。用 `dev-user` / `admin123` 登录即可，不是故障。

## 7. IDEA 本地启动（直接跑 austin-web）

> 与 Docker 部署不同，IDEA 里是**宿主机进程**，连不到 docker 网络里的服务名（`austin-mysql`/`austin-redis` 等），必须用 `localhost` + 宿主映射端口。

### 7.1 原因
[application.properties](file:///e:/Github/austin2.0/austin-web/src/main/resources/application.properties) 默认连接用 Docker 服务名：
- `spring.datasource.url=jdbc:mysql://austin-mysql:3306/austin`
- `spring.redis.host=austin-redis`

Docker 部署时同一网络可解析；IDEA 本地进程解析不了 → `HikariCP - Exception during pool initialization` / `Could not create connection to database server`。

### 7.2 解决办法：在运行配置加参数指向宿主机端口
IDEA → Run/Debug Configurations → **Program arguments**：

```
--austin.database.ip=localhost --austin.database.port=33306 --austin.redis.ip=localhost
```

- `33306` 是 MySQL 容器在宿主机映射的端口（容器内仍为 3306）。
- Redis 宿主端口保持 `6379`（无需改 port）。

### 7.3 推荐用 dev profile（本地单机友好）
[application-dev.properties](file:///e:/Github/austin2.0/austin-web/src/main/resources/application-dev.properties)：
- 端口 `7778`（非 7777）
- MQ 用 `eventBus`，无需 RabbitMQ/Kafka
- Nacos、XXL-JOB、hades 均关闭（少连外部组件）

在 Program arguments 里加 `--spring.profiles.active=dev` 即可。若走 test profile，则需同时连 Nacos/RabbitMQ/XXL-JOB，与本地进程网络不匹配，不建议在 IDEA 里用。