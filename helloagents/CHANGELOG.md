# CHANGELOG

## [0.4.15] - 2026-01-31

### 微调
- **[Chat Server]**: 移除 embed-path 配置与 iframe 兜底，嵌入信息仅由 embeddedId/supersetDomain 提供
  - 类型: 微调（无方案包）
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/processor/execute/SupersetChartProcessor.java:126-504
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetChartInfo.java:9-21
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetApiClient.java:73-350
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetPluginConfig.java:12-59
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetPluginProperties.java:12-57
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetPluginInitializer.java:77-98
  - 文件: launchers/standalone/src/main/resources/s2-config.yaml:47-68
  - 文件: launchers/standalone/src/test/resources/s2-config.yaml:35-55
  - 文件: helloagents/modules/chat-server.md:15-21
- **[Chat Server]**: 单图嵌入直接使用 dashboard guest token，移除 chart guest token 尝试
  - 类型: 微调（无方案包）
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetApiClient.java:72-98
  - 文件: helloagents/modules/chat-server.md:12-18
- **[Webapp/Chat-SDK]**: 移除 iframe 兜底，嵌入信息缺失时提示
  - 类型: 微调（无方案包）
  - 文件: webapp/packages/chat-sdk/src/components/ChatMsg/SupersetChart/index.tsx:33-214
  - 文件: webapp/packages/chat-sdk/src/components/ChatMsg/SupersetChart/index.test.tsx:7-92
  - 文件: webapp/packages/chat-sdk/src/common/type.ts:120-138
  - 文件: webapp/packages/supersonic-fe/src/pages/ChatPlugin/DetailModal.tsx:112-608
  - 文件: webapp/packages/supersonic-fe/src/pages/ChatPlugin/type.ts:1-25
  - 文件: helloagents/modules/webapp-chat-sdk.md:7-15
- **[Webapp/Chat-SDK]**: Superset 渲染判定改为 embeddedId/supersetDomain
  - 类型: 微调（无方案包）
  - 文件: webapp/packages/chat-sdk/src/components/ChatItem/ExecuteItem.tsx:34-140
  - 文件: helloagents/modules/webapp-chat-sdk.md:7-15
- **[Webapp/Chat-SDK]**: Superset 渲染判定放宽为 SUPERSET 模式 + fallback=false，缺失嵌入信息时提示
  - 类型: 微调（无方案包）
  - 文件: webapp/packages/chat-sdk/src/components/ChatItem/ExecuteItem.tsx:34-140
  - 文件: helloagents/modules/webapp-chat-sdk.md:7-15
- **[Build]**: Windows 前端构建脚本覆盖旧 webapp 产物，避免仍加载旧 bundle
  - 类型: 微调（无方案包）
  - 文件: assembly/bin/supersonic-build.bat:22-36
- **[Webapp]**: 增加 Superset Embed SDK 独立测试页
  - 类型: 微调（无方案包）
  - 文件: webapp/packages/supersonic-fe/public/superset-embed-test.html:1-86
- **[Webapp]**: 测试页改为本地加载 Superset Embed SDK bundle
  - 类型: 微调（无方案包）
  - 文件: webapp/packages/supersonic-fe/public/superset-embedded-sdk/index.js
  - 文件: webapp/packages/supersonic-fe/public/superset-embed-test.html:1-92
- **[Webapp]**: 测试页 SDK 路径改为相对路径，兼容 /webapp 前缀
  - 类型: 微调（无方案包）
  - 文件: webapp/packages/supersonic-fe/public/superset-embed-test.html:1-92
- **[Webapp]**: 测试页支持手动输入 guest token 与鉴权 token，输出 guest-token 响应
  - 类型: 微调（无方案包）
  - 文件: webapp/packages/supersonic-fe/public/superset-embed-test.html:1-120

## [0.4.14] - 2026-01-30

### 微调
- **[Chat Server]**: Superset 响应补充 embeddedId + supersetDomain，前端优先走官方 SDK 嵌入
  - 类型: 微调（无方案包）
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/processor/execute/SupersetChartProcessor.java:126-519
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetChartResp.java:9-31
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetChartInfo.java:9-23
- **[Webapp/Chat-SDK]**: 使用 embeddedId/supersetDomain 触发 SDK 嵌入并补充测试
  - 类型: 微调（无方案包）
  - 文件: webapp/packages/chat-sdk/src/components/ChatMsg/SupersetChart/index.tsx:33-219
  - 文件: webapp/packages/chat-sdk/src/components/ChatMsg/SupersetChart/index.test.tsx:7-92
  - 文件: webapp/packages/chat-sdk/src/common/type.ts:120-138
  - 文件: helloagents/modules/webapp-chat-sdk.md:7-15
  - 文件: helloagents/modules/chat-server.md:15-21

## [0.4.13] - 2026-01-31

### 微调
- **[Webapp/Chat-SDK]**: 推送到看板按钮文案调整，过滤对话生成的单图临时看板
  - 类型: 微调（无方案包）
  - 文件: webapp/packages/chat-sdk/src/components/ChatMsg/SupersetChart/index.tsx:21-279
  - 文件: webapp/packages/chat-sdk/src/components/ChatMsg/SupersetChart/index.test.tsx:1-77

## [0.4.12] - 2026-01-30

### 微调
- **[Chat Server]**: 单图 dashboard 回退时使用插件 embed-path，避免嵌入路径与 Superset 前缀不一致
  - 类型: 微调（无方案包）
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetApiClient.java:79-255
  - 文件: chat/server/src/test/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetApiClientTest.java:48-92
  - 文件: helloagents/modules/chat-server.md:21

## [0.4.11] - 2026-01-30

### 微调
- **[Chat Server]**: guest token 请求补充 user 字段，避免 Superset 500
  - 类型: 微调（无方案包）
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetApiClient.java:213-252
  - 文件: chat/server/src/test/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetApiClientTest.java:48-67
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetPluginConfig.java:52-56
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetPluginProperties.java:51-55
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetPluginInitializer.java:94-96
  - 文件: launchers/standalone/src/main/resources/s2-config.yaml:63-65
  - 文件: launchers/standalone/src/test/resources/s2-config.yaml:51-53
  - 文件: helloagents/modules/chat-server.md:21

## [0.4.10] - 2026-01-30

### 微调
- **[Headless/Sync]**: 时间维度格式转换为 Superset python_date_format，避免 dataset 更新 422
  - 类型: 微调（无方案包）
  - 文件: headless/server/src/main/java/com/tencent/supersonic/headless/server/sync/superset/SupersetSyncService.java:894-1021
  - 文件: headless/server/src/test/java/com/tencent/supersonic/headless/server/sync/superset/SupersetSyncServiceTest.java:161-167
  - 文件: helloagents/modules/headless-superset-sync.md:20-52

## [0.4.9] - 2026-01-29

### 变更
- **[Chat Server]**: Superset 嵌入改为嵌入式 dashboard，创建/复用嵌入配置并以 embedded uuid 生成 guest token
- **[Chat Server]**: 新增 Superset guest token 刷新接口（/api/chat/superset/guest-token）
- **[Chat Server]**: 自动为单图 dashboard 打标签（supersonic / supersonic-single-chart / supersonic-dataset-<id>）
- **[Webapp/Chat-SDK]**: 接入 @superset-ui/embedded-sdk 实现跨域匿名嵌入与 token 自动续期
- **[Webapp]**: Superset 插件配置 Embed 路径占位更新为 `/superset/embedded/{uuid}/`
- **[Launchers/Standalone]**: Superset embed-path 默认值更新为 `/superset/embedded/{uuid}/`

### 微调
- **[Chat Server]**: 单图 dashboard 通过更新 chart dashboards 关联，避免 /dashboard/{id}/charts 404
  - 类型: 微调（无方案包）
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetApiClient.java:111-118
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetApiClient.java:298-300
- **[Chat Server]**: 自动补全 Superset 图表基础 formData（queryColumns 缺失时从 queryResults 推断），避免 Data 配置为空
  - 类型: 微调（无方案包）
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/processor/execute/SupersetChartProcessor.java:110-405
  - 文件: chat/server/src/test/java/com/tencent/supersonic/chat/server/processor/execute/SupersetChartProcessorTest.java:1-86
- **[Chat Server]**: 修正 Superset dashboard tag 请求体为 properties.tags，并补充 tag/formData debug 日志
  - 类型: 微调（无方案包）
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetApiClient.java:120-158
  - 文件: chat/server/src/main/java/com/tencent/supersonic/chat/server/processor/execute/SupersetChartProcessor.java:110-352
  - 文件: chat/server/src/test/java/com/tencent/supersonic/chat/server/plugin/build/superset/SupersetApiClientTest.java:1-49

## [0.4.8] - 2026-01-29

### 变更
- **[Chat Server]**: Superset 图表生成改为运行时解析 dataset/database/schema（通过同步服务），不再依赖配置项
- **[Headless/Sync]**: 补充 datasetId → modelId 选择逻辑（优先 includesAll），支持运行时解析 Superset dataset
- **[Headless/Sync]**: Postgres JDBC 查询参数过滤 `stringtype`，避免 Superset SQLAlchemy 连接报错
- **[Headless/Sync]**: 数据库同步新增候选数据库日志（id/name/host/port/database/schema）
- **[Headless/Sync]**: 数据库同步/映射前先检查 Superset 已有库并输出期望连接信息（去除密码）
- **[Headless/Sync]**: 数据库同步输出解密后的用户名/密码日志便于核对
- **[Headless/Sync]**: 同步时强制加载带密码的数据库详情，确保 Superset 连接串包含密码
- **[Chat Server]**: Superset guest token 失败时自动创建临时 dashboard 并改用 dashboard embed
- **[Chat Server]**: Superset guest token 使用资源 UUID（chart/dashboard）以兼容只接受字符串 id 的版本
- **[Webapp]**: Superset 插件配置移除 dataset/database/schema 录入项
- **[Launchers/Standalone]**: 移除 Superset dataset/database/schema 与 LLM 选图模型/提示词配置项（由运行时解析或插件配置）

## [0.4.7] - 2026-01-29

### 新增
- **[Headless/Sync]**: 新增 Superset 数据库/数据集同步服务与映射策略（Model → Dataset）
  - 方案: [202601291034_superset-sync](plan/202601291034_superset-sync/)
- **[Headless/Sync]**: 增加实时事件触发、定时同步与失败重试机制
- **[Headless/API]**: 新增手动同步 API（数据库/数据集）
- **[Webapp]**: 数据库管理与数据集管理页新增“同步到 Superset”按钮
- **[Launchers/Standalone]**: 增加 Superset 同步参数与同步日志包的 debug 配置
- **[Headless/Tests]**: 补充 Superset 同步服务单元测试

## [0.4.6] - 2026-01-28

### 变更
- **[Launchers/Standalone]**: 补充 Superset dataset/database/schema 配置项以支持创建图表数据集
  - 类型: 微调（无方案包）
  - 文件: launchers/standalone/src/main/resources/s2-config.yaml:34
  - 文件: launchers/standalone/src/test/resources/s2-config.yaml:32

## [0.4.5] - 2026-01-28

### 修复
- **[Launchers/Standalone]**: 注册 SupersetChartProcessor 到 ExecuteResultProcessor 列表，确保 Superset 处理器生效
  - 类型: 微调（无方案包）
  - 文件: launchers/standalone/src/main/resources/META-INF/spring.factories:1

## [0.4.4] - 2026-01-28

### 变更
- **[Chat Server]**: Superset 选图 LLM 模型改为从插件配置读取，不再依赖 Agent 配置
- **[Webapp]**: Superset 插件配置页新增 LLM 选图模型与提示词设置

## [0.4.3] - 2026-01-28

### 变更
- **[Chat Server]**: Superset 图表类型选择改为规则 + LLM 结合，补充数据特征摘要并支持候选白/黑名单与 top-N
- **[Launchers/Standalone]**: Superset 配置新增 LLM 选图开关与候选白/黑名单参数

## [0.4.2] - 2026-01-28

### 变更
- **[Chat Server]**: 增强 Superset 调用链 debug 日志，覆盖执行器、处理器与 API 调用流程
  - 方案: [202601281612_superset-flow-logging](archive/2026-01/202601281612_superset-flow-logging/)
- **[Launchers/Standalone]**: Standalone 启用 Superset 调用链相关包的 debug 日志输出到 console
  - 方案: [202601281612_superset-flow-logging](archive/2026-01/202601281612_superset-flow-logging/)

## [0.4.1] - 2026-01-28

### 新增
- **[Chat Server]**: 新增 Superset 图表类型元数据清单 `viztype.json` 用于图表类型选择
  - 来源: `superset-frontend/src/visualizations/presets/MainPreset.js` + 插件 metadata
- **[Chat Server]**: 重写 Superset 图表类型选择逻辑，改为基于 `viztype.json` 解析并输出 Superset `viz_type`

## [0.4.0] - 2026-01-28

### 变更
- **[Chat Server]**: Superset 支持 JWT + CSRF 与 API key 认证策略
  - 方案: [202601281035_superset-auth-strategy](archive/2026-01/202601281035_superset-auth-strategy/)
  - 决策: superset-auth-strategy#D001(JWT 优先策略 + 内置会话缓存)
- **[Launchers/Standalone]**: 新增 Superset 认证策略与 JWT 配置项
  - 方案: [202601281035_superset-auth-strategy](archive/2026-01/202601281035_superset-auth-strategy/)

## [0.3.0] - 2026-01-28

### 变更
- **[Chat Server]**: Superset 集成改为 API 密钥鉴权并更新配置字段
  - 方案: [202601280942_superset-api-key](archive/2026-01/202601280942_superset-api-key/)
  - 决策: superset-api-key#D001(API 密钥通过 Authorization Bearer 头透传)
- **[Launchers/Standalone]**: Superset 配置改用 `api-key` 环境变量
  - 方案: [202601280942_superset-api-key](archive/2026-01/202601280942_superset-api-key/)

## [0.2.0] - 2026-01-27

### 新增
- **[Chat Server]**: 启动时自动配置 Superset 插件，支持无认证连接
  - 方案: [202601271654_superset-config](archive/2026-01/202601271654_superset-config/)
  - 决策: superset-config#D001(采用启动初始化器维护 Superset 配置)

### 变更
- **[Launchers/Standalone]**: 新增 Superset 默认配置并允许环境变量覆盖
  - 方案: [202601271654_superset-config](archive/2026-01/202601271654_superset-config/)

## [0.1.0] - 2026-01-27

### 微调
- **[Chat SDK]**: 默认优先渲染 Superset 图表，fallback 时回退到原有图表
  - 类型: 微调（无方案包）
  - 文件: webapp/packages/chat-sdk/src/components/ChatItem/ExecuteItem.tsx:6-171; webapp/packages/chat-sdk/src/components/ChatItem/ExecuteItem.test.tsx:1-65
