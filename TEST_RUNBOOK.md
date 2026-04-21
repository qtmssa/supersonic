# Supersonic 浏览器自动化运行指引

适用对象：后续在本仓库做浏览器自动化测试、页面验收、Playwright 调试的 Codex 会话。

最后实测时间：2026-04-02（Asia/Shanghai）

## 先看结论

- 默认不要直接假设 `9080` 空闲。2026-04-02 实测这台机器的 `9080` 已被另一个工作区里的 H2 版 Supersonic 占用。
- 浏览器自动化的最稳基线不是 `9000` 前端 dev server，而是“完整构建后，用独立端口启动 standalone，再直接访问 `/webapp/login`”。
- `supersonic` 的前后端不是一次编译出来的单体产物，而是分开编译、最后再打包整合。后续会话不要把“后端能编过”误当成“前端也已就绪”。
- 当前仓库的 bare `mvn` 已通过 `.mvn/maven.config` 固定到 workspace-local Maven repo：`supersonic/.symphony/m2/repository`。新 workspace 先跑 `bash .symphony/pre-build-workspace.sh`，后续 smoke/build 会复用同一仓库。
- 当前仓库的 `.env` 默认走 `postgres`，不是 `h2`。数据库不可达时，当前主仓库启动链不成立。
- `Superset` 对“图表/嵌入/同步”链路是硬依赖；对“仅打开登录页、仅做基础 UI 冒烟”不是硬依赖，但当前 `.env` 已启用相关配置，最好仍一起核可达性。
- `mf2s2-mcp`、`dbt-mcp`、`metricsys_ai` 目前只被识别为关联仓库，不是当前仓库编译和 standalone 启动的直接硬依赖。2026-04-02 本次构建与隔离实例启动都未依赖它们。

## 构建模型要先理解

浏览器自动化前，先把当前仓库的构建模型理解对：

- 后端编译链：`mvn -f "$projectDir" clean package -DskipTests -Dspotless.skip=true`
- 前端编译链：`webapp/start-fe-prod.sh`，内部会先装 workspace 依赖，再编 `chat-sdk`，再编 `supersonic-fe`
- 合包链：前端产物会被打成 `supersonic-webapp.tar.gz`，再和 `launchers/standalone/target/*-bin.tar.gz` 一起整合进最终 release 包

也就是说：

- `bash scripts/smoke.sh` 只证明后端最小单测链没坏
- `bash scripts/smoke.sh frontend` 只证明前端构建链没坏
- `./assembly/bin/supersonic-build.sh standalone` 才是“后端 + 前端都编完并打成 standalone 发布包”的整体验证

如果你的目标是浏览器自动化，不要跳过前端 smoke，也不要只看后端打包日志。

## 已实测通过的最小闭环

以下步骤是 2026-04-02 在当前仓库实际跑通的顺序：

1. 基础检查

```bash
bash scripts/doctor.sh
bash .symphony/pre-build-workspace.sh
bash scripts/smoke.sh
bash scripts/smoke.sh frontend
```

2. 完整构建

```bash
./assembly/bin/supersonic-build.sh standalone
```

这一步的真实含义不是“单次统一编译”，而是：

1. 先用 Maven 编译后端各模块和 `launchers/standalone`
2. 再单独进入 `webapp/` 跑前端生产构建
3. 最后把前端产物和 standalone 启动包合并成发布物

3. 核外部硬依赖

```bash
source .env
PGPASSWORD="$S2_DB_PASSWORD" psql -h "$S2_DB_HOST" -p "$S2_DB_PORT" -U "$S2_DB_USER" -d "$S2_DB_DATABASE" -c 'select 1 as db_ok;'
curl -I "$S2_SUPERSET_BASE_URL"
```

4. 用独立实例名和独立端口启动，避免撞到别的会话

```bash
S2_INSTANCE_ID=verify S2_SERVER_PORT=9081 ./assembly/bin/supersonic-systemd.sh start
```

5. 等日志里出现 `Tomcat started on port 9081` 再验 HTTP

```bash
systemctl --user status supersonic-standalone-verify.service --no-pager
journalctl --user -u supersonic-standalone-verify.service -n 120 --no-pager
curl -I http://127.0.0.1:9081/
```

6. 浏览器打开并登录

- 实测访问：`http://127.0.0.1:9081/`
- 实测会跳到：`/webapp/login`
- 实测可登录账号：`admin / 123456`
- 实测登录后页面：`/webapp/model/domain/1/overview`

## 推荐给 Codex 的标准启动方式

### 路径 A：浏览器自动化默认路径，优先使用

适用场景：Playwright 验证、页面冒烟、需要最小化代理和多进程干扰。

```bash
bash scripts/doctor.sh
bash scripts/smoke.sh
bash scripts/smoke.sh frontend
./assembly/bin/supersonic-build.sh standalone
S2_INSTANCE_ID=e2e S2_SERVER_PORT=9081 ./assembly/bin/supersonic-systemd.sh start
```

浏览器目标地址：

```text
http://127.0.0.1:9081/webapp/login
```

为什么推荐这条：

- 单 origin，浏览器直接打 standalone，不依赖前端 dev proxy。
- 能避开共享服务器上已有的 `9080` 占用。
- 能明确绑定到当前仓库刚构建出的 runtime 目录。
- `bash .symphony/pre-build-workspace.sh` 会先把 Maven local repo 固定到 workspace 内并做依赖预热，后续 `bash scripts/smoke.sh` / `mvn ...` 不再回写 `~/.m2/repository`。

### 路径 B：前端调试路径，只在确实要看热更新时使用

```bash
cd webapp
pnpm install
pnpm --filter supersonic-fe postinstall
pnpm --filter supersonic-fe start:osdev
```

实测地址：

```text
http://127.0.0.1:9000
```

但要注意：

- `webapp/packages/supersonic-fe/config/proxy.ts` 把 `/api/` 和 `/aibi/api/` 固定代理到 `http://127.0.0.1:9080`。
- 这意味着如果你自己的后端起在 `9081`，而 `9080` 又正好被别的 Supersonic 占着，那么 `9000` 页面会误连到别人的后端。
- 2026-04-02 实测 `9000` 可打开并登录，但这是因为 `9080` 上已经有另一个可用实例；因此 `9000` 不能作为默认自动化基线。
- `9000` 路径实测存在控制台噪音：React/AntD 旧告警、语言包警告、1 个静态资源 `404`。能用，但不干净。

## 当前环境的关键事实

### 构建与工具链

- Java：实测 `openjdk 21.0.10`
- Maven：实测 `3.6.3`
- Node：实测 `v20.20.1`
- pnpm：实测 `10.32.1`

说明：

- 仓库里的 `packageManager` 记录是 `pnpm@9.12.3+`，但当前环境用 `10.32.1` 仍能完成 `build:os-local` 和 `start:osdev`。
- 前端 dev 脚本 `webapp/start-fe-dev.sh` 明确要求 Node 18 或 20 LTS，不建议在其它 Node 大版本上赌运气。

### 配置加载链

- `launchers/standalone/src/main/resources/application.yaml` 会导入：
  - `optional:file:.env[.properties]`
  - `optional:file:../../.env[.properties]`
  - `classpath:s2-config.yaml`
- `assembly/bin/supersonic-systemd.sh` 和 `assembly/bin/supersonic-daemon.sh` 也会显式 `source "$projectDir/.env"`。
- 当前仓库 `.env` 默认是：
  - `S2_DB_TYPE=postgres`
  - `S2_SUPERSET_BASE_URL=http://10.0.12.244:8088/`
  - `S2_SUPERSET_AUTH_ENABLED=true`

结论：后续会话不要把 H2 当成默认事实。除非你主动切 profile，否则当前主仓库就是 PostgreSQL 路径。

## 外部依赖怎么分层

### 1. 当前启动硬依赖

#### PostgreSQL

- 来源：当前仓库 `.env`
- 实测：`psql ... -c 'select 1'` 返回成功
- 作用：当前 standalone 的默认 profile 是 `postgres`

#### Superset 6.0.0

- 来源：当前仓库 `.env`、`部署信息.md`
- 实测：`curl -I "$S2_SUPERSET_BASE_URL"` 返回 `302`
- 源码位置：`/raid/ai/metric/superset-6.0.0`
- 部署方式：`docker`
- 作用：图表生成、嵌入、数据库/数据集同步链路

### 2. 关联仓库，但不是本次编译/启动硬依赖

以下目录都实存于 `/raid/ai/metric/coding/`：

- `/raid/ai/metric/coding/mf2s2-mcp`
- `/raid/ai/metric/coding/dbt-mcp`
- `/raid/ai/metric/coding/metricsys_ai`

但 2026-04-02 本次核查中：

- 在 `.env`、`.env.example`、`assembly/`、`launchers/`、`webapp/`、`chat/`、`headless/`、`common/`、`README*`、`RUNBOOK.md` 里没有检出它们作为当前启动链的直接引用。
- 当前仓库完整构建成功。
- 当前仓库隔离实例启动成功。

因此当前结论是：

- 不要把它们写成“编译/启动 Supersonic 的必起项”。
- 只有在当前测试场景明确走跨项目联动时，再单独补它们的启动说明。

## 实测踩坑与处理

### 坑 1：`9080` 可能已经被别的会话占用

实测证据：

- 2026-04-02 这台机器上 `9080` 被另一个 `.symphony` 工作区里的 H2 版 Supersonic 占用。
- 直接启动本仓库的 `systemd` standalone 会撞端口。

排查命令：

```bash
ss -ltnp '( sport = :9080 )'
ps -ef | rg 'StandaloneLauncher|server.port=9080|supersonic_standalone'
```

处理建议：

- 自动化验证优先使用独立端口，例如 `9081`
- 同时设置 `S2_INSTANCE_ID`，避免 unit 名冲突

### 坑 2：`supersonic-systemd.sh start` 不是幂等的

实测证据：

- 同一个 unit 名执行过一次后，再跑 `./assembly/bin/supersonic-systemd.sh start`，可能报：
  - `Failed to start transient service unit: Unit ... already exists.`

原因：

- 这是 transient unit；停掉后 unit 还在，脚本再次 `systemd-run` 会冲突。

处理方式：

- 第一次起新实例：用脚本

```bash
S2_INSTANCE_ID=e2e S2_SERVER_PORT=9081 ./assembly/bin/supersonic-systemd.sh start
```

- 后续复用同一个实例：直接用 `systemctl`

```bash
systemctl --user start supersonic-standalone-e2e.service
systemctl --user stop supersonic-standalone-e2e.service
systemctl --user status supersonic-standalone-e2e.service --no-pager
journalctl --user -u supersonic-standalone-e2e.service -n 120 --no-pager
```

### 坑 3：`status active` 不等于已经能被浏览器访问

实测证据：

- unit 刚变成 `active (running)` 时，`curl` 仍可能瞬时失败。
- 必须等日志里出现 `Tomcat started on port ...` 再认定 HTTP 就绪。

### 坑 4：`9000` dev server 会把 API 代理到固定的 `9080`

这意味着：

- 如果你想用自己起在 `9081` 的 isolated backend，就不能直接相信 `9000`。
- 除非你确认 `9080` 就是你要连的那个实例，否则不要把 `9000` 当成浏览器自动化目标。

## 建议写给后续 Codex 会话的话

如果你只是想“把浏览器自动化先跑起来”，先照下面做，不要自己改路径：

```bash
bash scripts/doctor.sh
bash scripts/smoke.sh
bash scripts/smoke.sh frontend
./assembly/bin/supersonic-build.sh standalone
S2_INSTANCE_ID=e2e S2_SERVER_PORT=9081 ./assembly/bin/supersonic-systemd.sh start
journalctl --user -u supersonic-standalone-e2e.service -n 120 --no-pager
curl -I http://127.0.0.1:9081/
```

浏览器只打：

```text
http://127.0.0.1:9081/webapp/login
```

不要默认打：

- `http://127.0.0.1:9000`，除非你确认代理目标就是你自己的 backend
- `http://127.0.0.1:9080`，除非你确认端口没被其它工作区占用
