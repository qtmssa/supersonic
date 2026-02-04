package com.tencent.supersonic.chat.server.processor.execute;

import com.alibaba.fastjson.JSONObject;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.chat.server.agent.Agent;
import com.tencent.supersonic.chat.server.agent.AgentToolType;
import com.tencent.supersonic.chat.server.agent.PluginTool;
import com.tencent.supersonic.chat.server.plugin.ChatPlugin;
import com.tencent.supersonic.chat.server.plugin.build.ParamOption;
import com.tencent.supersonic.chat.server.plugin.build.WebBase;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetApiClient;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetChartCandidate;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetChartInfo;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetChartResp;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetDashboardInfo;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetPluginConfig;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetVizTypeSelector;
import com.tencent.supersonic.chat.server.pojo.ExecuteContext;
import com.tencent.supersonic.chat.server.service.PluginService;
import com.tencent.supersonic.common.pojo.QueryColumn;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.common.util.JsonUtil;
import com.tencent.supersonic.headless.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.headless.api.pojo.response.QueryState;
import com.tencent.supersonic.headless.server.sync.superset.SupersetDatasetInfo;
import com.tencent.supersonic.headless.server.sync.superset.SupersetSyncService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class SupersetChartProcessor implements ExecuteResultProcessor {

    public static final String QUERY_MODE = "SUPERSET";

    @Override
    public boolean accept(ExecuteContext executeContext) {
        QueryResult queryResult = executeContext.getResponse();
        if (queryResult == null || executeContext.getParseInfo() == null) {
            return false;
        }
        if (!QueryState.SUCCESS.equals(queryResult.getQueryState())) {
            return false;
        }
        String queryMode = queryResult.getQueryMode();
        return !StringUtils.equalsAnyIgnoreCase(queryMode, "WEB_PAGE", "WEB_SERVICE", "PLAIN_TEXT");
    }

    @Override
    public void process(ExecuteContext executeContext) {
        QueryResult queryResult = executeContext.getResponse();
        log.debug("superset process start, queryId={}, queryMode={}",
                executeContext.getRequest().getQueryId(),
                queryResult == null ? null : queryResult.getQueryMode());
        Optional<ChatPlugin> pluginOptional = resolveSupersetPlugin(executeContext);
        if (!pluginOptional.isPresent()) {
            log.debug("superset plugin not found for queryId={}",
                    executeContext.getRequest().getQueryId());
            return;
        }
        ChatPlugin plugin = pluginOptional.get();
        SupersetPluginConfig config =
                JsonUtil.toObject(plugin.getConfig(), SupersetPluginConfig.class);
        if (config == null || !config.isEnabled()) {
            log.debug("superset config disabled, pluginId={}, queryId={}", plugin.getId(),
                    executeContext.getRequest().getQueryId());
            return;
        }
        SupersetChartResp response = new SupersetChartResp();
        response.setName(plugin.getName());
        response.setPluginId(plugin.getId());
        response.setPluginType(plugin.getType());

        String sql = resolveSql(queryResult, executeContext.getParseInfo());
        List<SupersetVizTypeSelector.VizTypeItem> vizTypeCandidates =
                resolveVizTypeCandidates(config, queryResult, executeContext);
        String vizType = resolvePrimaryVizType(vizTypeCandidates);
        response.setVizType(vizType);
        if (StringUtils.isBlank(sql) || StringUtils.isBlank(config.getBaseUrl())
                || !config.hasValidAuthConfig()) {
            response.setFallback(true);
            response.setFallbackReason("superset config invalid");
            log.debug(
                    "superset fallback: invalid config, pluginId={}, hasSql={}, baseUrl={}, authOk={}",
                    plugin.getId(), StringUtils.isNotBlank(sql), config.getBaseUrl(),
                    config.hasValidAuthConfig());
            queryResult.setQueryMode(QUERY_MODE);
            queryResult.setResponse(response);
            return;
        }
        SupersetDatasetInfo datasetInfo = resolveSupersetDataset(executeContext);
        Long datasetId = datasetInfo == null ? null : datasetInfo.getId();
        Long databaseId = datasetInfo == null ? null : datasetInfo.getDatabaseId();
        String schema = datasetInfo == null ? null : datasetInfo.getSchema();
        if (datasetId == null && databaseId == null) {
            response.setFallback(true);
            response.setFallbackReason("superset dataset unresolved");
            log.debug("superset fallback: dataset unresolved, pluginId={}, dataSetId={}",
                    plugin.getId(), executeContext.getParseInfo() == null ? null
                            : executeContext.getParseInfo().getDataSetId());
            queryResult.setQueryMode(QUERY_MODE);
            queryResult.setResponse(response);
            return;
        }
        try {
            SupersetApiClient client = new SupersetApiClient(config);
            String chartName = buildChartName(executeContext, plugin);
            log.debug("superset build chart start, pluginId={}, vizType={}, chartName={}",
                    plugin.getId(), vizType, chartName);
            List<String> dashboardTags = buildDashboardTags(executeContext);
            List<SupersetChartCandidate> chartCandidates =
                    buildChartCandidates(client, vizTypeCandidates, sql, chartName, config,
                            queryResult, datasetId, databaseId, schema, dashboardTags);
            if (!chartCandidates.isEmpty()) {
                SupersetChartCandidate primary = chartCandidates.get(0);
                response.setChartId(primary.getChartId());
                response.setChartUuid(primary.getChartUuid());
                response.setGuestToken(primary.getGuestToken());
                response.setEmbeddedId(primary.getEmbeddedId());
                response.setSupersetDomain(primary.getSupersetDomain());
                response.setVizType(primary.getVizType());
                response.setVizTypeCandidates(chartCandidates);
            } else {
                throw new IllegalStateException("superset chart build failed");
            }
            response.setWebPage(buildWebPage(config));
            log.debug(
                    "superset build chart success, pluginId={}, chartId={}, chartUuid={}, guestToken={}",
                    plugin.getId(), response.getChartId(), response.getChartUuid(),
                    StringUtils.isNotBlank(response.getGuestToken()));
            List<SupersetDashboardInfo> dashboards = Collections.emptyList();
            try {
                dashboards = client.listDashboards();
            } catch (Exception ex) {
                log.warn("superset dashboard list load failed", ex);
            }
            response.setDashboards(dashboards);
            response.setFallback(false);
        } catch (Exception ex) {
            log.warn("superset chart build failed", ex);
            response.setFallback(true);
            response.setFallbackReason(ex.getMessage());
            log.debug("superset fallback: {}", ex.getMessage());
        }
        queryResult.setQueryMode(QUERY_MODE);
        queryResult.setResponse(response);
        log.debug("superset process complete, queryId={}, fallback={}",
                executeContext.getRequest().getQueryId(), response.isFallback());
    }

    private Optional<ChatPlugin> resolveSupersetPlugin(ExecuteContext executeContext) {
        PluginService pluginService = ContextUtils.getBean(PluginService.class);
        List<ChatPlugin> plugins = pluginService.getPluginList();
        if (CollectionUtils.isEmpty(plugins)) {
            return Optional.empty();
        }
        Agent agent = executeContext.getAgent();
        List<Long> pluginIds = resolvePluginIds(agent);
        if (!CollectionUtils.isEmpty(pluginIds)) {
            plugins = plugins.stream().filter(plugin -> pluginIds.contains(plugin.getId()))
                    .collect(Collectors.toList());
        }
        Long dataSetId = executeContext.getParseInfo().getDataSetId();
        return plugins.stream().filter(plugin -> "SUPERSET".equalsIgnoreCase(plugin.getType()))
                .filter(plugin -> matchDataSet(plugin, dataSetId)).findFirst();
    }

    private List<Long> resolvePluginIds(Agent agent) {
        if (agent == null) {
            return Collections.emptyList();
        }
        List<String> tools = agent.getTools(AgentToolType.PLUGIN);
        if (CollectionUtils.isEmpty(tools)) {
            return Collections.emptyList();
        }
        List<Long> pluginIds = new ArrayList<>();
        for (String tool : tools) {
            PluginTool pluginTool = JSONObject.parseObject(tool, PluginTool.class);
            if (pluginTool != null && !CollectionUtils.isEmpty(pluginTool.getPlugins())) {
                pluginIds.addAll(pluginTool.getPlugins());
            }
        }
        return pluginIds;
    }

    private boolean matchDataSet(ChatPlugin plugin, Long dataSetId) {
        if (plugin.isContainsAllDataSet()) {
            return true;
        }
        if (dataSetId == null) {
            return true;
        }
        return plugin.getDataSetList() != null && plugin.getDataSetList().contains(dataSetId);
    }

    private String resolveSql(QueryResult queryResult, SemanticParseInfo parseInfo) {
        if (queryResult != null && StringUtils.isNotBlank(queryResult.getQuerySql())) {
            return queryResult.getQuerySql();
        }
        if (parseInfo != null && parseInfo.getSqlInfo() != null) {
            return parseInfo.getSqlInfo().getCorrectedS2SQL();
        }
        return null;
    }

    private SupersetDatasetInfo resolveSupersetDataset(ExecuteContext executeContext) {
        if (executeContext == null || executeContext.getParseInfo() == null) {
            return null;
        }
        Long dataSetId = executeContext.getParseInfo().getDataSetId();
        if (dataSetId == null) {
            return null;
        }
        SupersetSyncService syncService;
        try {
            syncService = ContextUtils.getBean(SupersetSyncService.class);
        } catch (Exception ex) {
            log.debug("superset sync service missing", ex);
            return null;
        }
        SupersetDatasetInfo datasetInfo = syncService.resolveDatasetByDataSetId(dataSetId);
        if (datasetInfo == null) {
            log.debug("superset dataset resolve failed, dataSetId={}", dataSetId);
            return null;
        }
        log.debug(
                "superset dataset resolved, dataSetId={}, supersetDatasetId={}, databaseId={}, schema={}",
                dataSetId, datasetInfo.getId(), datasetInfo.getDatabaseId(),
                datasetInfo.getSchema());
        return datasetInfo;
    }

    private List<SupersetVizTypeSelector.VizTypeItem> resolveVizTypeCandidates(
            SupersetPluginConfig config, QueryResult queryResult, ExecuteContext executeContext) {
        if (StringUtils.isNotBlank(config.getVizType())
                && !"AUTO".equalsIgnoreCase(config.getVizType())) {
            SupersetVizTypeSelector.VizTypeItem configured =
                    SupersetVizTypeSelector.resolveItemByVizType(config.getVizType(), null);
            return configured == null ? Collections.emptyList()
                    : Collections.singletonList(configured);
        }
        String queryText = executeContext == null || executeContext.getRequest() == null ? null
                : executeContext.getRequest().getQueryText();
        Agent agent = executeContext == null ? null : executeContext.getAgent();
        return SupersetVizTypeSelector.selectCandidates(config, queryResult, queryText, agent);
    }

    private String resolvePrimaryVizType(List<SupersetVizTypeSelector.VizTypeItem> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "table";
        }
        SupersetVizTypeSelector.VizTypeItem primary = candidates.get(0);
        return primary == null || StringUtils.isBlank(primary.getVizType()) ? "table"
                : primary.getVizType();
    }

    private String buildChartName(ExecuteContext executeContext, ChatPlugin plugin) {
        Long queryId = executeContext.getRequest().getQueryId();
        String suffix =
                queryId == null ? String.valueOf(System.currentTimeMillis()) : queryId.toString();
        return "supersonic_" + plugin.getName() + "_" + suffix;
    }

    private List<String> buildDashboardTags(ExecuteContext executeContext) {
        List<String> tags = new ArrayList<>();
        tags.add("supersonic");
        tags.add("supersonic-single-chart");
        SemanticParseInfo parseInfo = executeContext == null ? null : executeContext.getParseInfo();
        if (parseInfo != null && parseInfo.getDataSet() != null
                && parseInfo.getDataSet().getDataSetId() != null) {
            tags.add("supersonic-dataset-" + parseInfo.getDataSet().getDataSetId());
        }
        return tags;
    }

    private List<SupersetChartCandidate> buildChartCandidates(SupersetApiClient client,
            List<SupersetVizTypeSelector.VizTypeItem> candidates, String sql, String chartName,
            SupersetPluginConfig config, QueryResult queryResult, Long datasetId, Long databaseId,
            String schema, List<String> dashboardTags) {
        if (client == null || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<SupersetChartCandidate> results = new ArrayList<>();
        Long resolvedDatasetId = datasetId;
        int limit = Math.min(3, candidates.size());
        for (int i = 0; i < limit; i++) {
            SupersetVizTypeSelector.VizTypeItem candidate = candidates.get(i);
            if (candidate == null || StringUtils.isBlank(candidate.getVizType())) {
                continue;
            }
            String vizType = candidate.getVizType();
            String candidateChartName = buildCandidateChartName(chartName, vizType, i);
            Map<String, Object> formData = buildFormData(config, queryResult, vizType);
            log.debug(
                    "superset chart formData prepared, vizType={}, keys={}, size={}, queryColumns={}, queryResults={}",
                    vizType, formData.keySet(), formData.size(),
                    queryResult.getQueryColumns() == null ? 0
                            : queryResult.getQueryColumns().size(),
                    queryResult.getQueryResults() == null ? 0
                            : queryResult.getQueryResults().size());
            SupersetChartInfo chartInfo = client.createEmbeddedChart(sql, candidateChartName,
                    vizType, formData, resolvedDatasetId, databaseId, schema, dashboardTags);
            if (resolvedDatasetId == null && chartInfo.getDatasetId() != null) {
                resolvedDatasetId = chartInfo.getDatasetId();
            }
            SupersetChartCandidate chartCandidate = new SupersetChartCandidate();
            chartCandidate.setVizType(vizType);
            chartCandidate.setVizName(candidate.getName());
            chartCandidate.setChartId(chartInfo.getChartId());
            chartCandidate.setChartUuid(chartInfo.getChartUuid());
            chartCandidate.setGuestToken(chartInfo.getGuestToken());
            chartCandidate.setEmbeddedId(chartInfo.getEmbeddedId());
            chartCandidate.setSupersetDomain(buildSupersetDomain(config));
            results.add(chartCandidate);
        }
        return results;
    }

    private String buildCandidateChartName(String baseName, String vizType, int index) {
        String safeVizType =
                StringUtils.defaultIfBlank(vizType, "candidate").replaceAll("[^a-zA-Z0-9_-]", "_");
        return baseName + "_" + safeVizType + "_" + (index + 1);
    }

    /**
     * 构建 Superset 图表的 formData，优先合并插件自定义配置。
     *
     * Args: config: Superset 插件配置。 queryResult: 查询结果。 vizType: 图表类型。
     *
     * Returns: 合并后的 formData。
     */
    Map<String, Object> buildFormData(SupersetPluginConfig config, QueryResult queryResult,
            String vizType) {
        Map<String, Object> autoFormData = buildAutoFormData(queryResult, vizType);
        Map<String, Object> customFormData = config == null ? null : config.getFormData();
        if (customFormData == null || customFormData.isEmpty()) {
            return autoFormData;
        }
        if (autoFormData.isEmpty()) {
            return customFormData;
        }
        Map<String, Object> merged = new HashMap<>(autoFormData);
        merged.putAll(customFormData);
        return merged;
    }

    /**
     * 根据查询结果自动生成基础的 Superset formData，避免 Data 配置为空。
     *
     * Args: queryResult: 查询结果。 vizType: 图表类型。
     *
     * Returns: 自动生成的 formData。
     */
    private Map<String, Object> buildAutoFormData(QueryResult queryResult, String vizType) {
        Map<String, Object> formData = new HashMap<>();
        List<QueryColumn> queryColumns = queryResult == null ? null : queryResult.getQueryColumns();
        if (CollectionUtils.isEmpty(queryColumns)) {
            queryColumns = resolveColumnsFromResults(queryResult);
            if (!CollectionUtils.isEmpty(queryColumns)) {
                List<String> fallbackColumns = queryColumns.stream().map(this::resolveColumnName)
                        .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
                log.debug("superset formData fallback to queryResults, columns={}",
                        fallbackColumns);
            }
        }
        if (CollectionUtils.isEmpty(queryColumns)) {
            return formData;
        }
        List<String> columnNames = queryColumns.stream().map(this::resolveColumnName)
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        if (CollectionUtils.isEmpty(columnNames)) {
            return formData;
        }
        if (isTableVizType(vizType)) {
            formData.put("query_mode", "raw");
            formData.put("all_columns", columnNames);
            formData.put("columns", columnNames);
            return formData;
        }
        String metricColumn = null;
        String dimensionColumn = null;
        String timeColumn = null;
        for (QueryColumn column : queryColumns) {
            String columnName = resolveColumnName(column);
            if (StringUtils.isBlank(columnName)) {
                continue;
            }
            if (timeColumn == null && isTimeColumn(column)) {
                timeColumn = columnName;
            }
            if (metricColumn == null && isNumericColumn(column)) {
                metricColumn = columnName;
            }
            if (dimensionColumn == null && !isNumericColumn(column) && !isTimeColumn(column)) {
                dimensionColumn = columnName;
            }
        }
        if (metricColumn != null) {
            formData.put("metrics", Collections.singletonList(metricColumn));
        }
        if (dimensionColumn != null) {
            formData.put("groupby", Collections.singletonList(dimensionColumn));
        }
        if (timeColumn != null) {
            formData.put("granularity_sqla", timeColumn);
        }
        if (formData.isEmpty()) {
            formData.put("query_mode", "raw");
            formData.put("all_columns", columnNames);
            formData.put("columns", columnNames);
        }
        return formData;
    }

    private List<QueryColumn> resolveColumnsFromResults(QueryResult queryResult) {
        List<Map<String, Object>> rows = queryResult == null ? null : queryResult.getQueryResults();
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyList();
        }
        Map<String, Object> row = rows.get(0);
        if (row == null || row.isEmpty()) {
            return Collections.emptyList();
        }
        List<QueryColumn> columns = new ArrayList<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String name = entry.getKey();
            if (StringUtils.isBlank(name)) {
                continue;
            }
            columns.add(new QueryColumn(name, resolveValueType(entry.getValue()), name));
        }
        return columns;
    }

    private String resolveValueType(Object value) {
        if (value == null) {
            return "STRING";
        }
        if (value instanceof Number) {
            return "NUMBER";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof java.time.temporal.TemporalAccessor
                || value instanceof java.util.Date) {
            return "TIMESTAMP";
        }
        return "STRING";
    }

    /**
     * 解析列名用于 Superset formData。
     *
     * Args: column: 查询列信息。
     *
     * Returns: 列名字符串。
     */
    private String resolveColumnName(QueryColumn column) {
        if (column == null) {
            return null;
        }
        if (StringUtils.isNotBlank(column.getBizName())) {
            return column.getBizName();
        }
        if (StringUtils.isNotBlank(column.getNameEn())) {
            return column.getNameEn();
        }
        return column.getName();
    }

    /**
     * 判断列是否为数值类型。
     *
     * Args: column: 查询列信息。
     *
     * Returns: 是否为数值列。
     */
    private boolean isNumericColumn(QueryColumn column) {
        if (column == null) {
            return false;
        }
        String type = StringUtils.defaultString(column.getType()).toUpperCase();
        return type.contains("INT") || type.contains("LONG") || type.contains("DOUBLE")
                || type.contains("FLOAT") || type.contains("DECIMAL") || type.contains("NUMBER")
                || type.contains("NUMERIC") || type.contains("BIGINT") || type.contains("SHORT");
    }

    /**
     * 判断列是否为时间类型。
     *
     * Args: column: 查询列信息。
     *
     * Returns: 是否为时间列。
     */
    private boolean isTimeColumn(QueryColumn column) {
        if (column == null) {
            return false;
        }
        String type = StringUtils.defaultString(column.getType()).toUpperCase();
        return type.contains("DATE") || type.contains("TIME") || type.contains("TIMESTAMP");
    }

    /**
     * 判断是否为表格类图表。
     *
     * Args: vizType: 图表类型。
     *
     * Returns: 是否为 table 类。
     */
    private boolean isTableVizType(String vizType) {
        return StringUtils.isNotBlank(vizType) && vizType.toLowerCase().contains("table");
    }

    private WebBase buildWebPage(SupersetPluginConfig config) {
        WebBase webBase = new WebBase();
        webBase.setUrl("");
        List<ParamOption> paramOptions = new ArrayList<>();
        if (config.getHeight() != null) {
            ParamOption heightOption = new ParamOption();
            heightOption.setParamType(ParamOption.ParamType.FORWARD);
            heightOption.setKey("height");
            heightOption.setValue(config.getHeight());
            paramOptions.add(heightOption);
        }
        webBase.setParamOptions(paramOptions);
        return webBase;
    }

    private String buildSupersetDomain(SupersetPluginConfig config) {
        String baseUrl = StringUtils.defaultString(config.getBaseUrl());
        if (StringUtils.isBlank(baseUrl)) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
