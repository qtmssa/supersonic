# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build Commands

### Backend (Java/Maven)

```bash
# Clean build (skip tests)
mvn clean package -DskipTests -Dspotless.skip=true

# Run all tests
mvn test

# Run single test class
mvn test -Dtest=ClassName

# Full CI build
mvn -B package --file pom.xml
```

**Requirements:** Java 21, Maven

### Frontend (pnpm/React)

```bash
cd webapp

# Install dependencies
pnpm install

# Prepare Umi temp files when dev/build reports missing src/.umi/umi.ts
pnpm --filter supersonic-fe postinstall

# Start dev server (port 9000)
pnpm --filter supersonic-fe start:osdev

# Production-like local build smoke
pnpm --filter supersonic-fe build:os-local
```

**Requirements:** Node.js >=16, pnpm 9.12.3+

### Quick Start

```bash
# Build full release
./assembly/bin/supersonic-build.sh standalone

# Start durable local service under systemd --user (preferred for CLI sessions)
./assembly/bin/supersonic-systemd.sh start
./assembly/bin/supersonic-systemd.sh status
./assembly/bin/supersonic-systemd.sh logs
./assembly/bin/supersonic-systemd.sh stop

# Start short-lived validation only
./assembly/bin/supersonic-daemon.sh start

# Stop short-lived daemon service
./assembly/bin/supersonic-daemon.sh stop
```

Visit http://localhost:9080 after startup.

Runtime notes:
- `assembly/bin/supersonic-daemon.sh` now auto-prepares `assembly/runtime/<app_name>` from `launchers/<service>/target/*-bin.tar.gz` and loads `supersonic/.env` when present.
- `assembly/bin/supersonic-systemd.sh` is the preferred durable launcher for local CLI sessions; it manages the Java process with `systemd --user` and supports `start|stop|restart|status|logs`.
- In short-lived CLI shells, bare background startup may still be reaped after the command returns. For a durable local service, prefer a real supervisor such as `systemd --user`, or keep the Java process in a foreground session.
- For browser automation, read `TEST_RUNBOOK.md` first. It contains the verified startup path, external dependency notes, the `9080` port-collision caveat, and the recommended isolated launch pattern using `S2_INSTANCE_ID` plus `S2_SERVER_PORT`.

## Architecture Overview

SuperSonic unifies **Chat BI** (LLM-powered) and **Headless BI** (semantic layer) paradigms.

### Core Modules

```
supersonic/
├── auth/           # Authentication & authorization (SPI-based)
├── chat/           # Chat BI module - LLM-powered Q&A interface
├── common/         # Shared utilities
├── headless/       # Headless BI - semantic layer with open API
├── launchers/      # Application entry points
│   ├── standalone/ # Combined Chat + Headless (default)
│   ├── chat/       # Chat-only service
│   └── headless/   # Headless-only service
└── webapp/         # Frontend React app (UmiJS 4 + Ant Design)
```

### Data Flow

1. **Knowledge Base**: Extracts schema from semantic models, builds dictionary/index for schema mapping
2. **Schema Mapper**: Identifies metrics/dimensions/entities/values in user queries
3. **Semantic Parser**: Generates S2SQL (semantic SQL) using rule-based and LLM-based parsers
4. **Semantic Corrector**: Validates and corrects semantic queries
5. **Semantic Translator**: Converts S2SQL to executable SQL

### Key Entry Points

- `StandaloneLauncher.java` - Combined service with `scanBasePackages: ["com.tencent.supersonic", "dev.langchain4j"]`
- `ChatLauncher.java` - Chat BI only
- `HeadlessLauncher.java` - Headless BI only

## Key Technologies

**Backend:** Spring Boot 3.3.9, MyBatis-Plus 3.5.10.1, LangChain4j 0.36.2, JSqlParser 4.9, Calcite 1.38.0

**Frontend:** React 18, UmiJS 4, Ant Design 5.17.4, ECharts 5.0.2, AntV G6/X6

**Databases:** MySQL, PostgreSQL (with pgvector), H2, ClickHouse, StarRocks, Presto, Trino, DuckDB

## Testing

**Java tests:** JUnit 5, Mockito. Located in `src/test/java/` of each module.

**Frontend verification:** `bash scripts/smoke.sh frontend` currently uses `build:os-local` as the stable smoke path. If dev/build reports missing `src/.umi/umi.ts`, run `pnpm --filter supersonic-fe postinstall` before restarting the dev server. The old Jest/Puppeteer entry in `webapp/packages/supersonic-fe/` is not a reliable default until its missing `tests/` assets are restored.

**Browser automation note:** Prefer a standalone instance on an isolated port such as `9081` over the `9000` dev server. The dev server proxies `/api` to `127.0.0.1:9080`, so it can silently talk to the wrong backend if another local Supersonic instance is already bound there.

**Evaluation scripts:** Python scripts in `evaluation/` directory for Text2SQL accuracy testing.

## Related Documentation

- [README.md](README.md) - English documentation
- [README_CN.md](README_CN.md) - Chinese documentation
- [Evaluation Guide](evaluation/README.md) - Text2SQL evaluation process
- [Browser Automation Runbook](TEST_RUNBOOK.md) - verified compile/start path for browser automation sessions

# 项目的部署信息
- 在文档 "部署信息.md" 中

# 项目的需求信息
- 在文件夹 "pr" 中
