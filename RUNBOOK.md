# RUNBOOK.md

## 适用范围
- `supersonic` 是主业务系统，包含 Chat BI、Headless BI、Standalone launcher 与前端 `webapp`。（状态: 已验证，来源: supersonic/AGENTS.md；supersonic/CURRENT_STATE.md）
- 本手册只回答“先跑什么、失败先看哪里、下一步怎么放大验证”，不替代 `README_CN.md` 与需求文档。（状态: 已验证，来源: AI_CONVENTIONS.md）

## 关键入口
- `bash scripts/doctor.sh`：检查 Java/Maven/Node/pnpm 与关键脚本、关键目录是否就绪。（状态: 已验证，来源: 本次第 3 阶段新增脚本；2026-03-17 当前环境执行通过）
- `bash scripts/smoke.sh`：默认执行 `DateUtilsTest`，用于确认 Maven 测试链最小可用。（状态: 已验证，来源: 本次第 3 阶段新增脚本；2026-03-17 当前环境执行通过）
- `bash scripts/smoke.sh frontend`：可选执行前端 smoke，当前使用 `build:os-local` 做有限时编译验证，不再碰运行中 dev server 的 `.umi` 临时文件。（状态: 已验证，来源: 2026-03-19 当前环境修复验证）
- `./assembly/bin/supersonic-build.sh standalone`：完整构建发布包。（状态: 已验证，来源: 2026-04-02 当前环境执行通过）
- `./assembly/bin/supersonic-daemon.sh start`：启动 Standalone 服务。（状态: 未验证，来源: supersonic/AGENTS.md）
- `./assembly/bin/supersonic-systemd.sh start`：在 `systemd --user` 下托管 Standalone 服务，适合 CLI 本地联调常驻运行；首次起新实例可用，复用已有 transient unit 时要改用 `systemctl --user start <unit>`。（状态: 已验证，来源: 2026-04-02 当前环境复核）
- `TEST_RUNBOOK.md`：浏览器自动化专用运行手册，包含外部依赖、隔离端口、多实例启动和 `9000/9080` 注意事项。（状态: 已验证，来源: 2026-04-02 当前环境新增）

## 最小排障顺序
1. 先执行 `bash scripts/doctor.sh`，确认基础命令、脚本和前后端目录都存在。
2. 如果只是确认仓库还能通过最小回归，执行 `bash scripts/smoke.sh`。
3. 如果要验证完整启动链，先执行 `./assembly/bin/supersonic-build.sh standalone`，再优先使用 `./assembly/bin/supersonic-systemd.sh start`。
4. 如果只是一次性前台或短时验证，可继续用 `./assembly/bin/supersonic-daemon.sh start`，但不要把它当作当前 CLI 环境下的可靠常驻托管方式。
5. 如果启动失败，优先查看 `assembly/bin/` 下脚本、`launchers/` 入口，以及 `./assembly/bin/supersonic-systemd.sh logs` 或运行生成的日志文件。
6. 如果是浏览器自动化，不要默认相信 `9080` 和 `9000`；先读 `TEST_RUNBOOK.md`，优先走独立实例名加独立端口的路径。

## Doctor 重点检查
- Java、Maven、Node、pnpm 是否可执行。
- `pom.xml`、`assembly/bin/supersonic-build.sh`、`assembly/bin/supersonic-daemon.sh`、`webapp/package.json` 是否存在。
- 当前仓库是否同时具备后端构建入口与前端 smoke 入口。

## Smoke 默认做什么
- 默认执行 `mvn -pl common -Dtest=DateUtilsTest test`。（状态: 已验证，来源: `supersonic/common/src/test/java/com/tencent/supersonic/common/DateUtilsTest.java`；2026-03-17 当前环境执行通过）
- 这个用例只覆盖公共模块，不依赖外部数据库或完整服务启动，适合作为“仓库还活着”的最小回归。
- `bash scripts/smoke.sh frontend` 当前会执行 `pnpm --dir webapp --filter supersonic-fe build:os-local`，用于验证前端构建链。（状态: 已验证，来源: 2026-03-19 当前环境修复验证）
- 如果前端开发页报 `src/.umi/umi.ts` 缺失，先执行 `pnpm --dir webapp --filter supersonic-fe postinstall`，再重启 `supersonic-fe` dev server。（状态: 已验证，来源: 2026-03-19 当前环境修复验证）
- 如果是更深的后端改动，再扩大到 `mvn test -Dtest=ClassName` 或模块级回归。

## 常见失败与判断
- `java` / `mvn` / `node` / `pnpm` 缺失：先解决本机依赖，不要直接怀疑业务代码。
- `doctor` 通过但 `smoke` 失败：优先判断是公共模块单测回归，还是 Maven/JDK 版本偏差。
- `build.sh`、`daemon.sh` 或 `supersonic-systemd.sh` 失败：优先看 `assembly/bin/` 环境脚本、`launchers/standalone`、`assembly/runtime/` 产物和对应日志。
- 前端相关问题：优先回到 `webapp/packages/supersonic-fe/`，先确认 `.umi` 是否已通过 `max setup` 生成，再判断是 dev 进程陈旧、构建链异常还是后端接口问题。

## 外部依赖与升级验证建议
- 更深层的启动验证通常还需要数据库、配置文件和发布产物，不应把它们塞进默认 `smoke`。
- 当前仓库 `.env` 默认走 PostgreSQL，并配置了 Superset；浏览器自动化前至少确认数据库可连、Superset 基础 HTTP 可达。（状态: 已验证，来源: 2026-04-02 当前环境复核）
- `mf2s2-mcp`、`dbt-mcp`、`metricsys_ai` 当前只确认是关联仓库，不是本仓库构建和 standalone 启动的直接硬依赖；不要无依据地把它们写成“必起”。（状态: 已验证，来源: 2026-04-02 当前环境代码与配置检索）
- 如果改动落在 `chat/`、`headless/`、`launchers/`、`webapp/`、`form-data-schema/`、`superset-spec/`，默认 `smoke` 之后应追加对应模块验证。（状态: 已验证，来源: supersonic/CURRENT_STATE.md）
- 变更完成后，记得把新的稳定入口或失败结论回填到 `CURRENT_STATE.md`、`README_AI.md` 或本手册，而不是只停留在临时聊天里。（状态: 已验证，来源: AI_CONVENTIONS.md）
